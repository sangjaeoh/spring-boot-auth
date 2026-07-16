package com.example.auth.app.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 어드민·백오피스 실행 앱(회원 검색/상세·강제 로그아웃·잠금/해제·권한 변경·로그인 이력·감사 조회).
 *
 * <p>도메인·infra·common 모듈을 컴포넌트 스캔으로 조립한다(모든 모듈이 {@code com.example.auth} 루트를
 * 공유). 관리자 API 접근 통제는 토큰 roles 클레임 기반이고, 크로스 스키마 read는
 * {@code infrastructure/query} 격리 구역에만 둔다.
 */
@SpringBootApplication(scanBasePackages = "com.example.auth")
@EntityScan({
    "com.example.auth.domain.auth.entity",
    "com.example.auth.domain.generic.entity",
    "com.example.auth.domain.user.entity"
})
@EnableJpaRepositories({
    "com.example.auth.domain.auth.repository",
    "com.example.auth.domain.generic.repository",
    "com.example.auth.domain.user.repository"
})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
