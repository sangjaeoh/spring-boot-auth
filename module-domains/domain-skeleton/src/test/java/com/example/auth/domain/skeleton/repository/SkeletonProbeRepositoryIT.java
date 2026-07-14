package com.example.auth.domain.skeleton.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.skeleton.entity.SkeletonProbe;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * walking skeleton E2E: Flyway 마이그레이션 → {@code ddl-auto=validate} → 저장/조회.
 *
 * <p>실 PostgreSQL(Testcontainers)에 대해 파이프라인 전 구간이 정합함을 증명한다(docs/code-quality.md의
 * H2 기각 근거).
 */
@SpringBootTest
@Import(JpaConfig.class)
@Testcontainers
class SkeletonProbeRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SkeletonProbeRepository repository;

    @Test
    void savesAndReadsBackThroughMigratedSchema() {
        SkeletonProbe probe = SkeletonProbe.create("walking-skeleton");

        repository.save(probe);

        Optional<SkeletonProbe> found = repository.findByLabel("walking-skeleton");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(probe.getId());
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
    }
}
