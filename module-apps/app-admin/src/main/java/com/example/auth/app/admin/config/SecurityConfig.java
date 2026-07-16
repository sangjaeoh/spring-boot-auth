package com.example.auth.app.admin.config;

import com.example.auth.common.web.security.JwtAuthenticationFilter;
import com.example.auth.common.web.security.ProblemDetailAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 관리자 API 보안 체인을 조립한다(stateless·CSRF off·Bearer JWT 필터 — 토큰 roles 클레임 기반 집행).
 *
 * <p>{@code /admin/**}는 ADMIN·SUPER_ADMIN 역할만 허용한다: 미인증은 401(엔트리포인트), 비관리자
 * 인증자는 403. 역할 변경 오퍼레이션은 메서드 가드({@code @PreAuthorize})로 SUPER_ADMIN을 추가
 * 요구한다(권한 매핑 정본은 usr {@code role_permission} 시드 — role:write는 SUPER_ADMIN 전용).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
                // 프레임워크 sendError의 ERROR 디스패치가 /error에서 401로 둔갑하지 않게 허용(app-api와 동일).
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        // 관리 엔드포인트는 분리된 관리 포트에만 매핑된다(내부망 전용 — app-api와 동일 방침).
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        .requestMatchers("/admin/**")
                        .hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
