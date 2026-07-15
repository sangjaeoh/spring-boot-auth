package com.example.auth.infra.messaging;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 메시징 IT용 부트 설정.
 *
 * <p>{@code @ComponentScan}을 두지 않아 JDBC·트랜잭션·Flyway(msg 스키마)만 부팅하고, 메시징 빈은 테스트가
 * {@code MessagingConfig}를 명시 Import해 조립한다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class MessagingTestApplication {}
