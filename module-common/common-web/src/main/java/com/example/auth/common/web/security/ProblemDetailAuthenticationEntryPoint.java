package com.example.auth.common.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 미인증 요청에 401 ProblemDetail(application/problem+json)을 쓴다.
 *
 * <p>본문은 열거 저항을 위해 사유를 세분하지 않는 고정 응답이다. 컨트롤러 예외의 ProblemDetail 변환은
 * 별도 핸들러가 담당한다.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY =
            "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"code\":\"AUTH_UNAUTHENTICATED\",\"detail\":\"인증이 필요합니다.\"}";

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
