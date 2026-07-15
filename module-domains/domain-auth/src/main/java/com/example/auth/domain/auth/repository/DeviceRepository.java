package com.example.auth.domain.auth.repository;

import com.example.auth.domain.auth.entity.Device;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 디바이스의 영속 포트다(Spring Data 인터페이스 = 도메인 포트).
 *
 * <p>소프트삭제 대상이 아니므로 base finder를 그대로 쓴다. 삭제는 물리 삭제다(지문 유니크와 재등록
 * 충돌 방지 — 엔티티 Javadoc 참조).
 */
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByUserIdAndFingerprint(UUID userId, String fingerprint);

    Optional<Device> findByIdAndUserId(UUID id, UUID userId);

    List<Device> findAllByUserIdOrderByLastAccessedAtDesc(UUID userId);

    void deleteByUserId(UUID userId);
}
