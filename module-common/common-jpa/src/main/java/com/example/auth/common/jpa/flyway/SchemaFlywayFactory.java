package com.example.auth.common.jpa.flyway;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * 스키마마다 독립 Flyway 인스턴스를 조립·실행한다.
 *
 * <p>도메인은 자기 스키마 마이그레이션을 {@code classpath:db/migration/{schema}}에 소유하고, 이 팩토리가
 * 스키마별로 {@code defaultSchema}를 그 스키마로 둔 Flyway를 만들어 실행한다. 그 결과 <b>history 테이블이
 * 스키마마다 따로</b> 서고 <b>버전 번호가 스키마별로 독립</b>(auth V1 / usr V1 충돌 없음)이다. Spring Boot의
 * 단일 Flyway는 {@code classpath:db/migration}을 재귀 스캔해 두 V1을 한 history로 봐 충돌하므로, 다중 스키마
 * 앱은 Boot Flyway auto-config를 끄고 이 팩토리를 쓴다(docs/architecture.md — 도메인 모듈 구조).
 */
public final class SchemaFlywayFactory {

    private SchemaFlywayFactory() {}

    /**
     * 주어진 스키마들을 각자 독립 Flyway로 순차 마이그레이션한다.
     */
    public static void migrateAll(DataSource dataSource, List<String> schemas) {
        for (String schema : schemas) {
            create(dataSource, schema).migrate();
        }
    }

    static Flyway create(DataSource dataSource, String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration/" + schema)
                .load();
    }
}
