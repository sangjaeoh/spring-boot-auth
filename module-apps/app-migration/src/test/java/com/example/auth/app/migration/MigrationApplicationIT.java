package com.example.auth.app.migration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * app-migration 스캐폴드 검증: 기동 시 Flyway가 실 PostgreSQL에 이력 테이블을 세운다.
 *
 * <p>실행기 + Flyway/DataSource 배선이 동작함을 증명한다(도메인 스키마 집약은 Phase 1).
 */
@SpringBootTest
@Testcontainers
class MigrationApplicationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayCreatesSchemaHistoryOnBoot() throws Exception {
        try (var connection = dataSource.getConnection();
                var tables = connection.getMetaData().getTables(null, null, "flyway_schema_history", null)) {
            assertThat(tables.next()).isTrue();
        }
    }
}
