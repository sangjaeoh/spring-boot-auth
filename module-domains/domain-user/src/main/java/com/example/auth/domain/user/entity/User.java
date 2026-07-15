package com.example.auth.domain.user.entity;

import com.example.auth.common.core.id.UuidV7Generator;
import com.example.auth.common.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * 회원 애그리거트 루트다. 사람의 캐노니컬 식별({@code UserId})·프로필·연락처·생명주기를 소유한다.
 *
 * <p>PK는 앱 생성 UUIDv7({@code @GeneratedValue} 없음)이며 두 서비스가 공유하는 정체성 계약이다. PII(실명·
 * 생년월일·휴대폰)는 봉투 암호화 컬럼으로 저장하고, 전화 동등 조회를 위해 별도 blind index를 든다. 상태는
 * 온보딩 사전조건 충족 후 ACTIVE로 생성된다(전이 메서드는 P4에서 writer가 등장할 때 추가).
 */
@Entity
@Table(schema = "usr", name = "users")
public class User extends BaseTimeEntity<UUID> {

    @Id
    @Column(name = "id")
    private UUID id;

    @Embedded
    private Profile profile;

    @Embedded
    private Contact contact;

    @Column(name = "contact_phone_bidx", length = 64)
    private String contactPhoneBidx;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private LifecycleStatus status;

    @Column(name = "ci_hash", length = 128)
    private String ciHash;

    protected User() {}

    private User(UUID id, Profile profile, Contact contact, String contactPhoneBidx, String ciHash) {
        this.id = id;
        this.profile = profile;
        this.contact = contact;
        this.contactPhoneBidx = contactPhoneBidx;
        this.ciHash = ciHash;
        this.status = LifecycleStatus.ACTIVE;
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

    @Override
    public UUID getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public Contact getContact() {
        return contact;
    }

    public String getContactPhoneBidx() {
        return contactPhoneBidx;
    }

    public LifecycleStatus getStatus() {
        return status;
    }

    public String getCiHash() {
        return ciHash;
    }
}
