package com.example.auth.external.social;

import com.example.auth.domain.auth.port.SocialIdentityOutcome;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.util.Set;

/**
 * 단일 발급자의 OIDC {@code id_token} 검증기다(Nimbus JOSE 위탁 — 서명·iss·aud·exp 검증).
 *
 * <p>4사 공통 RS256 + JWKS 게시 규약을 검증하고 subject·email·애플 릴레이 플래그만 꺼낸다. 원격 JWKS는
 * {@link JWKSourceBuilder} 기본 캐시·rate limit으로 조회한다(키 회전 대응).
 */
final class OidcIdTokenVerifier {

    private static final String APPLE_PRIVATE_EMAIL_CLAIM = "is_private_email";

    private final DefaultJWTProcessor<SecurityContext> processor;

    OidcIdTokenVerifier(String issuer, String clientId, JWKSource<SecurityContext> jwkSource) {
        this.processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                clientId, new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of("sub", "exp", "iat")));
    }

    /**
     * 게시된 원격 JWKS로 검증하는 인스턴스를 생성한다.
     */
    static OidcIdTokenVerifier forRemoteJwks(String issuer, String clientId, String jwksUri) {
        try {
            JWKSource<SecurityContext> jwkSource =
                    JWKSourceBuilder.create(URI.create(jwksUri).toURL()).build();
            return new OidcIdTokenVerifier(issuer, clientId, jwkSource);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("JWKS URI가 올바르지 않습니다: " + jwksUri, e);
        }
    }

    SocialIdentityOutcome verify(String idToken) {
        try {
            JWTClaimsSet claims = processor.process(idToken, null);
            String email = claims.getStringClaim("email");
            Object privateEmail = claims.getClaim(APPLE_PRIVATE_EMAIL_CLAIM);
            boolean privateRelay = privateEmail != null && Boolean.parseBoolean(privateEmail.toString());
            return new SocialIdentityOutcome.Verified(claims.getSubject(), email, privateRelay);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            return new SocialIdentityOutcome.Failed(
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
