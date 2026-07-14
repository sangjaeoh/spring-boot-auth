package com.example.auth.domain.skeleton;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 도메인 모듈 통합 테스트용 부트 설정.
 *
 * <p>도메인 모듈은 실행 앱(@SpringBootApplication)을 갖지 않으므로, 테스트가 JPA 슬라이스를 부팅할
 * 진입점을 test 소스가 제공한다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class SkeletonTestApplication {}
