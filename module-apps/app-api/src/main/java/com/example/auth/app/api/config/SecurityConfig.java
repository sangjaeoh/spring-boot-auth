package com.example.auth.app.api.config;

import com.example.auth.common.web.security.JwtAuthenticationFilter;
import com.example.auth.common.web.security.ProblemDetailAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 라우트별 보안 체인을 조립한다(stateless·CSRF off·Bearer JWT 필터).
 *
 * <p>필터·엔트리포인트 빈은 common-web이 제공하고, 라우트 정책만 앱이 소유한다. 로그인·토큰 재발급은
 * permitAll, 그 외는 인증 필요다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 프레임워크 sendError(역직렬화 400 등)의 ERROR 디스패치가 /error에서 401로 둔갑하지 않게 허용.
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(
                                "/.well-known/jwks.json",
                                "/auth/login",
                                "/auth/token/refresh",
                                "/auth/password/reset/initiate",
                                "/auth/password/reset/complete",
                                "/auth/registration/**",
                                "/auth/social/login",
                                "/auth/social/registration/complete")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
