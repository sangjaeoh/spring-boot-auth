package com.example.auth.domain.user.service;

import static java.util.Objects.requireNonNull;

import com.example.auth.domain.user.entity.CiRegistry;
import com.example.auth.domain.user.entity.ConsentAction;
import com.example.auth.domain.user.entity.ConsentRecord;
import com.example.auth.domain.user.entity.Contact;
import com.example.auth.domain.user.entity.Email;
import com.example.auth.domain.user.entity.IdentityVerification;
import com.example.auth.domain.user.entity.PhoneNumber;
import com.example.auth.domain.user.entity.Profile;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.entity.VerificationResult;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.CiRegistryRepository;
import com.example.auth.domain.user.repository.ConsentRecordRepository;
import com.example.auth.domain.user.repository.IdentityVerificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 완료의 {@code CreateUser} 명령을 수행한다 — 한 트랜잭션에서 {@code User(ACTIVE)} 생성 →
 * {@code ConsentRecord} append → {@code CiRegistry.link}(유니크 hard-enforce) → {@code IdentityVerification}
 * 연결을 원자 수행하고 {@code UserId}를 반환한다.
 *
 * <p>"한 트랜잭션 하나의 애그리거트" 원칙의 문서화된 예외다(docs/architecture.md 트랜잭션 경계 —
 * 온보딩 가입 완료). 멱등하다: {@code verificationRef}가 이미 회원에 연결돼 있으면(직전 커밋의 영수증)
 * 신규 쓰기 없이 그 {@code UserId}를 재반환한다. 동의는 버퍼 조기 검증과 별개로 여기서 현행 약관에
 * 재검증한다(버퍼~완료 사이 약관 개정 경합 — 최종 권위). CI 중복은 읽기 판정으로 409를 만들고, 경합
 * 이중생성은 {@code ci_hash} 유니크 인덱스가 backstop한다.
 */
@Service
public class UserRegistrationProcessor {

    private final IdentityVerificationRepository identityVerificationRepository;
    private final ConsentValidator consentValidator;
    private final CiUniquenessValidator ciUniquenessValidator;
    private final UserAppender userAppender;
    private final ConsentRecordRepository consentRecordRepository;
    private final CiRegistryRepository ciRegistryRepository;

    public UserRegistrationProcessor(
            IdentityVerificationRepository identityVerificationRepository,
            ConsentValidator consentValidator,
            CiUniquenessValidator ciUniquenessValidator,
            UserAppender userAppender,
            ConsentRecordRepository consentRecordRepository,
            CiRegistryRepository ciRegistryRepository) {
        this.identityVerificationRepository = identityVerificationRepository;
        this.consentValidator = consentValidator;
        this.ciUniquenessValidator = ciUniquenessValidator;
        this.userAppender = userAppender;
        this.consentRecordRepository = consentRecordRepository;
        this.ciRegistryRepository = ciRegistryRepository;
    }

    /**
     * 검증된 본인인증·동의 선택으로 활성 회원을 원자 생성하고 {@code UserId}를 반환한다(재실행 시 기존
     * {@code UserId} 재반환).
     *
     * @param loginEmail 인증의 로그인 이메일 — 연락용 이메일({@code contactEmail})의 최초 seed
     * @throws UserException 동의 재검증 실패 시(400), CI가 활성 회원에 이미 연결돼 있으면(409)
     */
    @Transactional
    public UUID register(UUID verificationRef, String ciHash, String loginEmail, Map<TermsType, Integer> consents) {
        IdentityVerification verification = getVerification(verificationRef);
        UUID linkedUserId = verification.getUserId();
        if (linkedUserId != null) {
            return linkedUserId;
        }

        consentValidator.validateForSignup(consents);
        ciUniquenessValidator.checkAvailable(ciHash);

        VerificationResult identity = requireNonNull(verification.getResult());
        Profile profile = Profile.of(identity.name(), identity.birthDate(), identity.gender());
        Contact contact = Contact.of(Email.of(loginEmail), PhoneNumber.of(identity.carrier(), identity.phone()));
        UUID userId = userAppender.register(profile, contact, ciHash);

        Instant now = Instant.now();
        List<ConsentRecord> records = new ArrayList<>();
        consents.forEach(
                (type, version) -> records.add(ConsentRecord.create(userId, type, version, ConsentAction.AGREE, now)));
        consentRecordRepository.saveAll(records);
        ciRegistryRepository.save(CiRegistry.link(ciHash, userId, now));
        verification.attachUser(userId);
        return userId;
    }

    private IdentityVerification getVerification(UUID verificationRef) {
        // 세션이 보관한 서버 발급 참조만 도달하므로 부재는 외부 입력이 아니라 호출자 버그다.
        return identityVerificationRepository
                .findById(verificationRef)
                .orElseThrow(() -> new IllegalArgumentException("본인인증이 존재하지 않습니다: " + verificationRef));
    }
}
