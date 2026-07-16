package com.example.auth.domain.generic.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.common.jpa.config.JpaConfig;
import com.example.auth.domain.generic.entity.AuditLog;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 감사 로그 영속 E2E: append·전/후 값 왕복과 WORM 강제(UPDATE/DELETE는 raw SQL 경로까지 DB 트리거가
 * 차단)를 실 PostgreSQL로 검증한다.
 */
@SpringBootTest
@Import(JpaConfig.class)
@Testcontainers
class AuditLogPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appendsAndReadsBackWithBeforeAfterValues() {
        String target = UUID.randomUUID().toString();
        AuditLog saved = auditLogRepository.save(AuditLog.create(
                "admin-1",
                "admin.member.lock",
                target,
                "{\"lockState\":\"NONE\"}",
                "{\"lockState\":\"ADMIN_LOCKED\"}",
                "{\"ip\":\"127.0.0.1\"}",
                Instant.now()));

        AuditLog found = auditLogRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getBeforeValue()).isEqualTo("{\"lockState\":\"NONE\"}");
        assertThat(found.getAfterValue()).isEqualTo("{\"lockState\":\"ADMIN_LOCKED\"}");
        assertThat(auditLogRepository
                        .findByTargetOrderByAtDesc(target, PageRequest.of(0, 10))
                        .getContent())
                .extracting(AuditLog::getId)
                .containsExactly(saved.getId());
    }

    @Test
    void blocksUpdateAndDeleteAtDatabaseLevel() {
        AuditLog saved = auditLogRepository.save(
                AuditLog.create("admin-1", "admin.member.unlock", "t", null, null, null, Instant.now()));

        assertThatThrownBy(() ->
                        jdbc.update("update generic.audit_log set actor = 'tampered' where id = ?", saved.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbc.update("delete from generic.audit_log where id = ?", saved.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(auditLogRepository.findById(saved.getId()).orElseThrow().getActor())
                .isEqualTo("admin-1");
    }
}
