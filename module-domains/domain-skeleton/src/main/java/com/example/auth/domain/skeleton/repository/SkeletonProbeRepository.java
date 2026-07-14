package com.example.auth.domain.skeleton.repository;

import com.example.auth.domain.skeleton.entity.SkeletonProbe;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * walking skeleton 프로브의 영속 포트다(Spring Data 인터페이스 = 도메인 포트).
 *
 * <p>소프트삭제 대상이 아니므로 base finder를 그대로 쓴다(docs/architecture.md).
 */
public interface SkeletonProbeRepository extends JpaRepository<SkeletonProbe, UUID> {

    Optional<SkeletonProbe> findByLabel(String label);
}
