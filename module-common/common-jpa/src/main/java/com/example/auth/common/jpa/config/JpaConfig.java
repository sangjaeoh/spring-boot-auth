package com.example.auth.common.jpa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing을 활성화한다.
 *
 * <p>{@code @CreatedDate}·{@code @LastModifiedDate}가 채워지도록 하는 공통 설정이다. 실행 앱·테스트는
 * 이 설정을 임포트해 {@link com.example.auth.common.jpa.entity.BaseTimeEntity}의 시각 필드를 채운다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
