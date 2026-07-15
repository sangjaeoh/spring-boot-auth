package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 회원 애그리거트 루트다. 사람의 캐노니컬 식별({@code UserId})·프로필·연락처·생명주기를 소유한다.
 *
 * <p>PK는 앱 생성 UUIDv7({@code @GeneratedValue} 없음)이며 두 서비스가 공유하는 정체성 계약이다. PII(실명·
 * 생년월일·휴대폰)는 봉투 암호화 컬럼으로 저장하고, 전화 동등 조회를 위해 별도 blind index를 든다. 상태는
 * 온보딩 사전조건 충족 후 ACTIVE로 생성된다.
 *
 * <p>생명주기 전이는 {@code statusVersion}을 단조 증가시킨다 — 인증의 {@code userStatus} 스냅샷 동기화가
 * 이 버전으로 순서 역전을 방지한다. 탈퇴는 PII를 즉시 파기(null 소거)하며 PII 필드는 그때부터 null이다
 * (WITHDRAWN 이외 상태에서는 항상 존재).
 */
@Entity
@Table(schema = "usr", name = "users")
public class User extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Embedded
    @Nullable
    private Profile profile;

    @Embedded
    @Nullable
    private Contact contact;

    @Column(name = "contact_phone_bidx", length = 64)
    @Nullable
    private String contactPhoneBidx;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private LifecycleStatus status;

    @Column(name = "status_version")
    private long statusVersion;

    @Column(name = "ci_hash", length = 128)
    @Nullable
    private String ciHash;

    @Column(name = "last_login_at")
    @Nullable
    private Instant lastLoginAt;

    @Column(name = "dormant_at")
    @Nullable
    private Instant dormantAt;

    @Column(name = "dormancy_notified_at")
    @Nullable
    private Instant dormancyNotifiedAt;

    @Column(name = "withdrawn_at")
    @Nullable
    private Instant withdrawnAt;

    protected User() {}

    private User(UUID id, Profile profile, Contact contact, String contactPhoneBidx, String ciHash) {
        this.id = id;
        this.profile = profile;
        this.contact = contact;
        this.contactPhoneBidx = contactPhoneBidx;
        this.ciHash = ciHash;
        this.status = LifecycleStatus.ACTIVE;
        this.statusVersion = 0;
    }

    /**
     * 본인인증·필수동의 사전조건을 충족한 뒤 활성 회원을 생성한다.
     *
     * <p>{@code contactPhoneBidx}는 서비스가 평문 전화로 미리 계산해 전달한다(엔티티는 blind index 재료를
     * 주입받지 않는다).
     */
    public static User create(Profile profile, Contact contact, String ciHash, String contactPhoneBidx) {
        return new User(UuidV7Generator.generate(), profile, contact, contactPhoneBidx, ciHash);
    }

    /**
     * 연락용 이메일을 변경한다(휴대폰 유지). 로그인 식별자({@code loginEmail})와 독립이다.
     */
    public void changeContactEmail(Email newEmail) {
        // 탈퇴 회원은 인증이 불가능해 이 경로에 도달하지 못한다 — contact 부재는 호출자 버그다.
        this.contact = Contact.of(newEmail, Objects.requireNonNull(contact).contactPhone());
    }

    /**
     * 탈퇴 전이한다 — WITHDRAWN + PII 즉시 파기(프로필·연락처·blind index·CI 해시 null 소거).
     *
     * <p>탈퇴 후 CI 해시 보존은 {@code CiRegistry} tombstone이 단독 소유한다(분리보관). {@code withdrawnAt}은
     * 동의이력 보존창(탈퇴 후 5년)의 기산점으로 남는다.
     *
     * @throws UserException 이미 탈퇴한 회원이면(409)
     */
    public void withdraw(Instant at) {
        if (status == LifecycleStatus.WITHDRAWN) {
            throw new UserException(UserErrorCode.USER_ALREADY_WITHDRAWN);
        }
        this.status = LifecycleStatus.WITHDRAWN;
        this.statusVersion++;
        this.withdrawnAt = at;
        this.dormantAt = null;
        this.dormancyNotifiedAt = null;
        this.profile = null;
        this.contact = null;
        this.contactPhoneBidx = null;
        this.ciHash = null;
    }

    /**
     * 로그인 관측을 반영한다 — 최근 로그인 시각을 단조 갱신하고 휴면 사전통지를 리셋한다(접속으로 휴면
     * 카운트다운이 새로 시작). 종료 상태(WITHDRAWN)에서는 무시한다(지연 이벤트 멱등 흡수).
     */
    public void recordLogin(Instant at) {
        if (status == LifecycleStatus.WITHDRAWN) {
            return;
        }
        if (lastLoginAt == null || lastLoginAt.isBefore(at)) {
            this.lastLoginAt = at;
        }
        this.dormancyNotifiedAt = null;
    }

    /**
     * 휴면 전환한다(장기 미접속 배치).
     *
     * @throws UserException ACTIVE가 아니면(409)
     */
    public void makeDormant(Instant at) {
        if (status != LifecycleStatus.ACTIVE) {
            throw new UserException(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = LifecycleStatus.DORMANT;
        this.statusVersion++;
        this.dormantAt = at;
    }

    /**
     * 휴면을 해제한다(유저 권위의 OTP 재인증 후에만 호출된다). 해제 시각을 접속으로 간주해 즉시 재휴면을
     * 막는다.
     *
     * @throws UserException DORMANT가 아니면(409)
     */
    public void reactivate(Instant at) {
        if (status != LifecycleStatus.DORMANT) {
            throw new UserException(UserErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = LifecycleStatus.ACTIVE;
        this.statusVersion++;
        this.dormantAt = null;
        this.dormancyNotifiedAt = null;
        this.lastLoginAt = at;
    }

    /**
     * 휴면 사전통지 발송 사실을 기록한다(30일 전 통지 — 중복 통지 방지 기준).
     */
    public void markDormancyNotified(Instant at) {
        this.dormancyNotifiedAt = at;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public @Nullable Profile getProfile() {
        return profile;
    }

    public @Nullable Contact getContact() {
        return contact;
    }

    public @Nullable String getContactPhoneBidx() {
        return contactPhoneBidx;
    }

    public LifecycleStatus getStatus() {
        return status;
    }

    public long getStatusVersion() {
        return statusVersion;
    }

    public @Nullable String getCiHash() {
        return ciHash;
    }

    public @Nullable Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public @Nullable Instant getDormantAt() {
        return dormantAt;
    }

    public @Nullable Instant getDormancyNotifiedAt() {
        return dormancyNotifiedAt;
    }

    public @Nullable Instant getWithdrawnAt() {
        return withdrawnAt;
    }
}
