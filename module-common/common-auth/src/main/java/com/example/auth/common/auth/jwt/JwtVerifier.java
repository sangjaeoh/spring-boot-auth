package com.example.auth.common.auth.jwt;

import com.example.auth.common.auth.exception.InvalidTokenException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Access 토큰의 서명·만료를 검증하고 클레임을 추출한다.
 *
 * <p>서명 위변조·만료·형식 오류는 {@link InvalidTokenException}(401)으로 통일한다.
 */
public class JwtVerifier {

    private final JwtDecoder decoder;

    public JwtVerifier(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * 토큰을 검증하고 {@link AccessClaims}를 반환한다.
     *
     * @throws InvalidTokenException 서명·만료·형식이 유효하지 않을 때
     */
    public AccessClaims verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String subject = jwt.getSubject();
            String sid = jwt.getClaimAsString("sid");
            Instant expiresAt = jwt.getExpiresAt();
            if (subject == null || sid == null || expiresAt == null) {
                throw new InvalidTokenException();
            }
            @Nullable List<String> roles = jwt.getClaimAsStringList("roles");
            return new AccessClaims(
                    UUID.fromString(subject), UUID.fromString(sid), roles == null ? List.of() : roles, expiresAt);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException();
        }
    }
}
