package com.example.auth.app.migration;

import com.example.auth.common.jpa.flyway.SchemaFlywayFactory;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 설정된 스키마들을 각자 독립 Flyway로 마이그레이션한다.
 *
 * <p>스키마 목록({@code app.migration.schemas})은 이 앱이 조립하는 도메인 스키마다 — 각 도메인 모듈이
 * {@code classpath:db/migration/{schema}}에 마이그레이션을 소유하고, 이 앱은 그 모듈들을 런타임 의존해
 * 리소스를 클래스패스에 싣는다. Boot의 단일 Flyway auto-config는 비활성(다중 스키마 버전 충돌 회피 —
 * application.yml exclude)이고 {@link SchemaFlywayFactory}가 스키마별 실행을 소유한다.
 */
@Component
public class SchemaMigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final List<String> schemas;

    public SchemaMigrationRunner(DataSource dataSource, @Value("${app.migration.schemas}") String schemasCsv) {
        this.dataSource = dataSource;
        this.schemas = Arrays.stream(schemasCsv.split(",", -1))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        SchemaFlywayFactory.migrateAll(dataSource, schemas);
    }
}
