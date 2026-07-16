package com.example.auth.infra.keystore;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * 키스토어 IT용 부트 설정.
 *
 * <p>{@code @ComponentScan}을 두지 않아 JDBC·트랜잭션·Flyway(keyring 스키마)만 부팅하고, 스토어 빈은
 * 테스트가 직접 조립한다(봉투암호는 결정적 fake — 실 AES-GCM은 infra-crypto 테스트가 커버).
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class KeystoreTestApplication {}
