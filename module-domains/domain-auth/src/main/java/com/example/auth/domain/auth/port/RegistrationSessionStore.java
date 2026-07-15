package com.example.auth.domain.auth.port;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationType;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 온보딩 세션(RegistrationSession 애그리거트)의 영속 포트다(Redis TTL).
 *
 * <p>세션·챌린지와 같은 Redis-TTL 애그리거트라 도메인이 포트를 선언하고 infra-redis가 구현한다
 * (IMPLEMENTATION_PLAN §1). TTL 만료 = 자동 파기이며, 만료 후 {@code mark*}/{@code attach*}가 키를
 * 부활시키지 않아야 한다(TTL 없는 좀비 세션 금지 — 영속 PENDING 금지의 Redis 판). 저장소 불가 시 전
 * 연산이 {@code AuthErrorCode.SESSION_STORE_UNAVAILABLE}(503)로 fail-closed한다 — 온보딩은 핫패스가
 * 아니고, 부재 오보(404)는 클라이언트에게 가입 재시작을 강요한다.
 */
public interface RegistrationSessionStore {

    /**
     * 온보딩 세션을 저장하고 TTL을 원자적으로 건다(TTL 없는 키가 잔존하는 중간 상태 금지). 챌린지
     * 바인딩은 발급 시점에 {@code attach*}로 채운다.
     */
    void create(UUID registrationId, RegistrationType type, String tokenHash, String loginEmail, Duration ttl);

    /**
     * 세션 스냅샷을 반환한다. 부재·만료면 empty.
     */
    Optional<RegistrationSnapshot> find(UUID registrationId);

    /**
     * 이메일 챌린지 바인딩을 최신 발급분으로 교체한다(이전 발급분은 스텝 검증에 쓸 수 없게 된다).
     *
     * @return 세션 부재(만료 포함)면 {@code false}
     */
    boolean attachEmailChallenge(UUID registrationId, String challengeId);

    /**
     * 휴대폰 챌린지 바인딩을 최신 발급분으로 교체한다.
     *
     * @return 세션 부재(만료 포함)면 {@code false}
     */
    boolean attachPhoneChallenge(UUID registrationId, String challengeId);

    /**
     * 이메일 인증 스텝을 완료로 마킹한다.
     *
     * @return 세션 부재(만료 포함)면 {@code false}
     */
    boolean markEmailVerified(UUID registrationId);

    /**
     * 휴대폰 인증 스텝을 완료로 마킹한다.
     *
     * @return 세션 부재(만료 포함)면 {@code false}
     */
    boolean markPhoneVerified(UUID registrationId);

    /**
     * 본인인증 스텝을 완료로 마킹하고 {@code verificationRef}·{@code ciHash}를 원자 저장한다.
     *
     * @return 세션 부재(만료 포함)면 {@code false}
     */
    boolean markIdentityVerified(UUID registrationId, UUID verificationRef, String ciHash);

    /**
     * 필수동의 스텝을 완료로 마킹하고 동의 선택값을 원자 버퍼링한다.
     *
     * @return 세션 부재(만료 포함)면 {@code false}
     */
    boolean markRequiredConsented(UUID registrationId, List<ConsentSelection> consents);
}
