package com.example.auth.app.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 인증 서비스 공개 API 실행 앱.
 *
 * <p>도메인·infra·common 모듈을 컴포넌트 스캔으로 조립한다(모든 모듈이 {@code com.example.auth} 루트를
 * 공유). 엔티티·리포지토리 스캔은 도메인 패키지로 명시한다.
 */
@SpringBootApplication(scanBasePackages = "com.example.auth")
@EntityScan({"com.example.auth.domain.auth.entity", "com.example.auth.domain.user.entity"})
@EnableJpaRepositories({"com.example.auth.domain.auth.repository", "com.example.auth.domain.user.repository"})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
