package com.example.auth.domain.generic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;

/**
 * 감사 로그 리포지토리 표면의 WORM 계약을 강제한다 — update/delete 오퍼레이션이 표면에 등장하면 실패한다
 * ({@code JpaRepository} 상속 복원·파생 delete 추가에 대한 회귀 방지).
 */
class AuditLogRepositoryContractTest {

    @Test
    void exposesNoUpdateOrDeleteOperations() {
        for (Method method : AuditLogRepository.class.getMethods()) {
            assertThat(method.getName())
                    .as("WORM 리포지토리 표면에 수정·삭제 오퍼레이션 금지: %s", method)
                    .doesNotStartWith("delete")
                    .doesNotStartWith("remove")
                    .doesNotStartWith("update");
            assertThat(method.isAnnotationPresent(Modifying.class))
                    .as("WORM 리포지토리에 @Modifying 쿼리 금지: %s", method)
                    .isFalse();
        }
    }

    @Test
    void doesNotInheritCrudSurface() {
        assertThat(org.springframework.data.repository.CrudRepository.class.isAssignableFrom(AuditLogRepository.class))
                .as("CrudRepository 상속은 delete 계열을 표면에 노출한다")
                .isFalse();
    }
}
