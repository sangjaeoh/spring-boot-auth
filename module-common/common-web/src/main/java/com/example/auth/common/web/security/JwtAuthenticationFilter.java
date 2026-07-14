package com.example.auth.common.web.security;

import com.example.auth.common.auth.exception.InvalidTokenException;
import com.example.auth.common.auth.jwt.AccessClaims;
import com.example.auth.common.auth.jwt.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer Access 토큰을 검증하고 세션 O(1) 유효성까지 통과한 요청만 인증 컨텍스트를 채운다.
 *
 * <p>서명·만료가 유효해도 세션이 무효(로그아웃·강제종료·재사용 감지)면 인증하지 않는다. 무효 토큰·무효
 * 세션은 익명으로 진행하고 보호 리소스의 401은 엔트리포인트가 담당한다(제품 명제: 실시간 무효화).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtVerifier jwtVerifier;
    private final SessionValidationPort sessionValidation;

    public JwtAuthenticationFilter(JwtVerifier jwtVerifier, SessionValidationPort sessionValidation) {
        this.jwtVerifier = jwtVerifier;
        this.sessionValidation = sessionValidation;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null
                && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            AccessClaims claims = jwtVerifier.verify(token);
            if (sessionValidation.isActive(claims.userId(), claims.sessionId())) {
                var authorities = claims.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();
                var principal = new AuthUser(claims.userId(), claims.sessionId(), claims.roles());
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
            }
        } catch (InvalidTokenException ignored) {
            // 무효 토큰은 익명으로 진행한다.
        }
    }
}
