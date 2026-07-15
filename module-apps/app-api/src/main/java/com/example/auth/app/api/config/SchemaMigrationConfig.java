package com.example.auth.app.api.config;

import com.example.auth.common.jpa.flyway.SchemaFlywayFactory;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 앱이 영속하는 스키마(auth·usr)를 스키마별 독립 Flyway로 마이그레이션한다.
 *
 * <p>Boot 단일 Flyway auto-config는 {@code db/migration}을 재귀 스캔해 auth V1·usr V1을 한 history로 봐
 * 충돌하므로 {@code FlywayAutoConfiguration}만 exclude하고(JPA auto-config는 유지) {@code SchemaFlywayFactory}
 * 실행으로 대체한다. {@code entityManagerFactory}가 이 빈에 dependsOn 순서화되어 {@code ddl-auto=validate}
 * 전에 두 스키마 마이그레이션이 완료된다.
 */
@Configuration
public class SchemaMigrationConfig {

    /**
     * 마이그레이션 완료를 나타내는 순서 마커다(범용 {@code List<String>} 빈은 컬렉션 주입 의미론과 겹친다).
     */
    public record MigratedSchemas(List<String> schemas) {}

    /**
     * 설정된 스키마들을 마이그레이션하고 그 목록을 반환한다(EMF가 dependsOn하는 순서 마커).
     */
    @Bean
    public MigratedSchemas migratedSchemas(
            DataSource dataSource, @Value("${app.migration.schemas}") String schemasCsv) {
        List<String> schemas = Arrays.stream(schemasCsv.split(",", -1))
                .map(String::trim)
                .filter(schema -> !schema.isEmpty())
                .toList();
        SchemaFlywayFactory.migrateAll(dataSource, schemas);
        return new MigratedSchemas(schemas);
    }

    /**
     * {@code entityManagerFactory}가 마이그레이션 완료 후 생성되도록 의존 순서를 배선한다.
     */
    @Bean
    static BeanFactoryPostProcessor entityManagerFactoryDependsOnMigrations() {
        return beanFactory -> {
            BeanDefinition definition = beanFactory.getBeanDefinition("entityManagerFactory");
            String[] dependsOn = definition.getDependsOn();
            if (dependsOn == null) {
                definition.setDependsOn("migratedSchemas");
                return;
            }
            String[] merged = Arrays.copyOf(dependsOn, dependsOn.length + 1);
            merged[dependsOn.length] = "migratedSchemas";
            definition.setDependsOn(merged);
        };
    }
}
