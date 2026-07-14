package com.example.auth.common.auth.config;

import com.example.auth.common.auth.jwt.JwtIssuer;
import com.example.auth.common.auth.jwt.JwtVerifier;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT 서명/검증 원자재를 조립한다.
 *
 * <p>dev는 기동 시 RSA 2048 키페어를 생성한다(단일 인스턴스·JVM 수명 동안 안정). JWKS 노출·90일 회전은
 * 후속(§7). 프로덕션은 관리형 키로 대체한다.
 */
@Configuration
public class JwtCryptoConfig {

    @Bean
    public RSAKey jwtRsaKey() throws JOSEException {
        return new RSAKeyGenerator(2048).keyID("dev").generate();
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey jwtRsaKey) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwtRsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey jwtRsaKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(jwtRsaKey.toRSAPublicKey()).build();
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
