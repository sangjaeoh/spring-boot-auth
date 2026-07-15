package com.example.auth.domain.user;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 도메인 모듈 영속 테스트용 부트 설정.
 *
 * <p>{@code @ComponentScan}을 두지 않아 포트 의존 서비스는 자동 등록되지 않고 JPA·Flyway·리포지토리만
 * 부팅한다(crypto 포트 구현은 테스트가 fake로 명시 등록 — 계층상 domain은 infra-crypto 미의존).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class UserDomainTestApplication {}
