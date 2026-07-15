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
 * app-migration 멀티스키마 검증: 기동 시 SchemaFlywayFactory가 auth·usr을 각자 독립 Flyway로 마이그레이션한다.
 *
 * <p>두 스키마의 실제 테이블 존재 + <b>스키마별 독립 history 테이블</b>을 확인해, 단일 Flyway로는 불가능한
 * (auth V1·usr V1 버전 충돌) 다중 스키마 마이그레이션이 성립함을 실증한다.
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
    void migratesAllConfiguredSchemasWithIndependentHistories() throws Exception {
        assertTableExists("auth", "auth_account");
        assertTableExists("usr", "users");
        // 스키마별 독립 history — SchemaFlywayFactory가 defaultSchema를 각 스키마에 둔 결과.
        assertTableExists("auth", "flyway_schema_history");
        assertTableExists("usr", "flyway_schema_history");
    }

    private void assertTableExists(String schema, String table) throws Exception {
        try (var connection = dataSource.getConnection();
                var tables = connection.getMetaData().getTables(null, schema, table, null)) {
            assertThat(tables.next()).as("%s.%s 존재", schema, table).isTrue();
        }
    }
}
