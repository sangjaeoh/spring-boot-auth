package com.example.auth.common.auth.config;

import com.example.auth.common.auth.jwt.JwtIssuer;
import com.example.auth.common.auth.jwt.JwtVerifier;
import com.example.auth.common.auth.jwt.SigningKeyRing;
import com.example.auth.common.auth.jwt.SigningKeyRotationScheduler;
import com.example.auth.common.auth.jwt.SigningKeyStore;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 서명/검증 원자재를 회전 키링 위에 조립한다.
 *
 * <p>서명은 현재 키로만, 검증은 {@code kid}로 현재 키 + 유예 키를 해석한다(회전 이후에도 유예 창 동안 검증
 * 성립). 두 {@link JWKSource}는 매 호출 키링 스냅샷을 읽어 회전을 빈 재생성 없이 반영하고, 미지 {@code kid}는
 * 스토어 재적재 후 한 번 재해석해 다른 인스턴스의 회전 직후 토큰도 즉시 검증한다. JWKS 게시는 app-api가
 * 소유한다.
 */
@Configuration
@EnableScheduling
public class JwtCryptoConfig {

    /**
     * 회전 키링을 조립한다. 유예 창이 Access TTL의 2배 미만이면 기동을 거부한다.
     *
     * @throws IllegalStateException 유예 창이 {@code 2×accessTtlMinutes} 미만일 때(회전 후 토큰이 만료 전
     *     축출돼 간헐 401이 나는 오설정 — DOMAIN_MODEL: grace ≥ 2×Access TTL)
     */
    @Bean
    public SigningKeyRing signingKeyRing(
            SigningKeyStore signingKeyStore,
            @Value("${auth.jwt.jwks.grace-minutes:30}") int graceMinutes,
            @Value("${auth.access.ttl-minutes:15}") int accessTtlMinutes) {
        if (graceMinutes < 2 * accessTtlMinutes) {
            throw new IllegalStateException(
                    "JWKS 유예(grace) %d분은 Access TTL(%d분)의 2배 이상이어야 한다".formatted(graceMinutes, accessTtlMinutes));
        }
        return new SigningKeyRing(signingKeyStore, Duration.ofMinutes(graceMinutes));
    }

    /**
     * 전 싱글턴 생성 후(스키마 마이그레이션 완료 후) 키링을 적재·부트스트랩해 오설정을 기동 시점에 드러낸다.
     */
    @Bean
    public SmartInitializingSingleton signingKeyRingInitializer(SigningKeyRing signingKeyRing) {
        return signingKeyRing::initialize;
    }

    /**
     * 현재 키 나이 기준 자동 회전 트리거를 조립한다(기본 주기 90일 — ISO-8601 {@code P90D}).
     */
    @Bean
    public SigningKeyRotationScheduler signingKeyRotationScheduler(
            SigningKeyRing signingKeyRing, @Value("${auth.jwt.jwks.rotation-period:P90D}") Duration rotationPeriod) {
        return new SigningKeyRotationScheduler(signingKeyRing, rotationPeriod);
    }

    @Bean
    public JwtEncoder jwtEncoder(SigningKeyRing signingKeyRing) {
        JWKSource<SecurityContext> signingSource =
                (jwkSelector, context) -> jwkSelector.select(new JWKSet(signingKeyRing.currentSigningKey()));
        return new NimbusJwtEncoder(signingSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(SigningKeyRing signingKeyRing) {
        JWKSource<SecurityContext> verificationSource = (jwkSelector, context) -> {
            List<JWK> selected = jwkSelector.select(signingKeyRing.publicJwkSet(Instant.now()));
            if (!selected.isEmpty()) {
                return selected;
            }
            // 미지 kid — 다른 인스턴스의 회전 직후일 수 있다. 스토어에서 재적재 후 한 번 재해석한다
            // (키 테이블은 수 행이라 위조 kid 폭주에도 적재 비용이 유계다).
            signingKeyRing.refresh();
            return jwkSelector.select(signingKeyRing.publicJwkSet(Instant.now()));
        };
        return NimbusJwtDecoder.withJwkSource(verificationSource)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    @Bean
    public JwtIssuer jwtIssuer(JwtEncoder jwtEncoder, @Value("${auth.jwt.issuer:auth-service}") String issuer) {
        return new JwtIssuer(jwtEncoder, issuer);
    }

    @Bean
    public JwtVerifier jwtVerifier(JwtDecoder jwtDecoder) {
        return new JwtVerifier(jwtDecoder);
    }
}
