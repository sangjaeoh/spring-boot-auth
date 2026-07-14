package com.example.auth.common.web.config;

import com.example.auth.common.auth.jwt.JwtVerifier;
import com.example.auth.common.web.handler.GlobalExceptionHandler;
import com.example.auth.common.web.security.JwtAuthenticationFilter;
import com.example.auth.common.web.security.ProblemDetailAuthenticationEntryPoint;
import com.example.auth.common.web.security.SessionValidationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * common-web가 제공하는 재사용 웹 보안 빈을 조립한다.
 *
 * <p>앱이 이 설정을 임포트해 필터·엔트리포인트·예외 핸들러를 얻고, 라우트별 {@code SecurityFilterChain}만
 * 자기 앱에서 조립한다. {@link SessionValidationPort} 구현은 앱이 제공한다.
 */
@Configuration
public class WebSecurityBeans {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtVerifier jwtVerifier, SessionValidationPort sessionValidation) {
        return new JwtAuthenticationFilter(jwtVerifier, sessionValidation);
    }

    @Bean
    public ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint() {
        return new ProblemDetailAuthenticationEntryPoint();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
