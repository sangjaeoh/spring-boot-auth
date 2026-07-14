package com.example.auth.app.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Flyway 마이그레이션 독립 실행 앱.
 *
 * <p>기동 시 Flyway가 스키마 마이그레이션을 적용한다. 런타임 데이터 접근(JPA)은 두지 않는다.
 */
@SpringBootApplication
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }
}
