package com.example.auth.domain.user.service;

import static java.util.Objects.requireNonNull;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.user.entity.ConsentAction;
import com.example.auth.domain.user.entity.ConsentState;
import com.example.auth.domain.user.entity.NotificationChannel;
import com.example.auth.domain.user.entity.TermsDocument;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.event.ConsentGiven;
import com.example.auth.domain.user.event.ConsentWithdrawn;
import com.example.auth.domain.user.exception.UserErrorCode;
import com.example.auth.domain.user.exception.UserException;
import com.example.auth.domain.user.repository.ConsentStateRepository;
import com.example.auth.domain.user.repository.TermsDocumentRepository;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용 중 동의/철회를 수행한다(가입 최초 동의는 {@code CreateUser} 트랜잭션이 소유).
 *
 * <p>철회는 선택 약관에만 허용한다 — 필수 약관 철회는 이용 불가를 뜻하므로 탈퇴 경로로만 처리한다.
 * 통합 이벤트는 커밋 후 전달되어({@code AFTER_COMMIT}) 마케팅 수신 설정 동기화가 소비한다.
 */
@Service
public class ConsentProcessor {

    private final ConsentValidator consentValidator;
    private final ConsentAppender consentAppender;
    private final TermsDocumentRepository termsDocumentRepository;
    private final ConsentStateRepository consentStateRepository;
    private final MessagePublisher messagePublisher;

    public ConsentProcessor(
            ConsentValidator consentValidator,
            ConsentAppender consentAppender,
            TermsDocumentRepository termsDocumentRepository,
            ConsentStateRepository consentStateRepository,
            MessagePublisher messagePublisher) {
        this.consentValidator = consentValidator;
        this.consentAppender = consentAppender;
        this.termsDocumentRepository = termsDocumentRepository;
        this.consentStateRepository = consentStateRepository;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 현행 버전에 대한 동의를 append한다(재동의 포함). 채널은 마케팅 동의의 채널 한정 선택에만 쓴다.
     *
     * @throws UserException 현행 발효 버전과 불일치·미지 유형이면(400)
     */
    @Transactional
    public void agree(UUID userId, TermsType termsType, int termsVersion, @Nullable NotificationChannel channel) {
        consentValidator.validateCurrentVersion(termsType, termsVersion);
        Instant now = Instant.now();
        consentAppender.append(userId, termsType, termsVersion, ConsentAction.AGREE, channel, now);
        messagePublisher.publish(new ConsentGiven(userId, termsType, termsVersion, channel, now));
    }

    /**
     * 선택 약관 동의를 철회한다.
     *
     * @throws UserException 필수 약관이면(400), 미지 유형이면(404), 철회할 유효 동의가 없으면(404)
     */
    @Transactional
    public void withdraw(UUID userId, TermsType termsType) {
        TermsDocument document = termsDocumentRepository
                .findByType(termsType)
                .orElseThrow(() -> new UserException(UserErrorCode.TERMS_TYPE_NOT_FOUND));
        if (document.isRequired()) {
            throw new UserException(UserErrorCode.REQUIRED_CONSENT_WITHDRAWAL);
        }
        ConsentState state = consentStateRepository
                .findByUserIdAndTermsType(userId, termsType)
                .filter(current -> current.getCurrentAction() == ConsentAction.AGREE)
                .orElseThrow(() -> new UserException(UserErrorCode.CONSENT_NOT_FOUND));
        Instant now = Instant.now();
        consentAppender.append(
                userId, termsType, requireNonNull(state.getAgreedVersion()), ConsentAction.WITHDRAW, null, now);
        messagePublisher.publish(new ConsentWithdrawn(userId, termsType, now));
    }
}
