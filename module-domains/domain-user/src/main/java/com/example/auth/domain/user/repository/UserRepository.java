package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.LifecycleStatus;
import com.example.auth.domain.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 회원 애그리거트 리포지토리다.
 *
 * <p>{@code contactPhoneBidx}는 <b>비유니크</b>(같은 전화가 재가입·번호 재할당으로 여러 행에 실재 가능 —
 * 활성 1인 불변식은 phone이 아니라 CiRegistry가 강제)라 조회는 다건을 반환한다. 휴면 후보 판정의 미접속
 * 기산점은 {@code coalesce(lastLoginAt, createdAt)}이다 — 접속 관측이 없는 회원은 가입 시각부터 센다.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByContactPhoneBidx(String contactPhoneBidx);

    @Query("select u from User u where u.status = :status and u.dormancyNotifiedAt is null"
            + " and coalesce(u.lastLoginAt, u.createdAt) <= :cutoff")
    List<User> findDormancyNoticeCandidates(@Param("status") LifecycleStatus status, @Param("cutoff") Instant cutoff);

    @Query("select u from User u where u.status = :status and u.dormancyNotifiedAt <= :noticeCutoff"
            + " and coalesce(u.lastLoginAt, u.createdAt) <= :inactivityCutoff")
    List<User> findDormancyTransitionCandidates(
            @Param("status") LifecycleStatus status,
            @Param("noticeCutoff") Instant noticeCutoff,
            @Param("inactivityCutoff") Instant inactivityCutoff);

    List<User> findByStatusAndWithdrawnAtBefore(LifecycleStatus status, Instant cutoff);
}
