package com.example.auth.common.auth.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * 세션에 바인딩된 Access 토큰(JWT, RS256)을 발급한다.
 *
 * <p>{@code sid} 클레임에 세션ID를 실어 매 요청 서버 세션검증과 결합한다(무효 세션은 서명이 유효해도
 * 거부된다 — docs 제품 명제).
 */
public class JwtIssuer {

    private final JwtEncoder encoder;
    private final String issuer;

    public JwtIssuer(JwtEncoder encoder, String issuer) {
        this.encoder = encoder;
        this.issuer = issuer;
    }

    /**
     * 주어진 세션·주체·역할로 만료 {@code ttl}인 Access 토큰을 서명해 반환한다.
     */
    public String issueAccess(UUID userId, UUID sessionId, List<String> roles, Duration ttl, Instant now) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("sid", sessionId.toString())
                .claim("roles", List.copyOf(roles))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
