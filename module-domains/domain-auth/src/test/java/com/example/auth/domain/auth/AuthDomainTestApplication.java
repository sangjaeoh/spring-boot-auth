package com.example.auth.domain.auth;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 도메인 모듈 영속 테스트용 부트 설정.
 *
 * <p>{@code @ComponentScan}을 두지 않아 포트 의존 {@code @Service} 빈은 등록되지 않고 JPA·Flyway·
 * 리포지토리만 부팅한다(포트 의존 서비스는 app-api E2E에서 실 인프라로 검증).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class AuthDomainTestApplication {}
