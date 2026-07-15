package com.example.auth.infra.redis;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.entity.RegistrationStep;
import com.example.auth.domain.auth.entity.RegistrationType;
import com.example.auth.domain.auth.exception.AuthErrorCode;
import com.example.auth.domain.auth.exception.AuthException;
import com.example.auth.domain.auth.port.RegistrationSessionStore;
import com.example.auth.domain.auth.port.RegistrationSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 기반 온보딩 세션 스토어다(TTL = 자동 파기).
 *
 * <p>단일 hash 키({@code reg:<registrationId>})만 만져 Cluster 슬롯 안전하다. 스텝 마킹은 EXISTS 가드
 * Lua로 수행해 만료된 세션을 부활시키지 않는다. 저장소 불가 시 전 연산이 503으로 fail-closed한다 —
 * 부재 오보(404)는 클라이언트에게 가입 재시작을 강요하므로 장애를 정직하게 알린다.
 */
@Component
public class RedisRegistrationSessionStore implements RegistrationSessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRegistrationSessionStore.class);

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_TOKEN_HASH = "tokenHash";
    private static final String FIELD_LOGIN_EMAIL = "loginEmail";
    private static final String FIELD_EMAIL_CHALLENGE_ID = "emailChallengeId";
    private static final String FIELD_PHONE_CHALLENGE_ID = "phoneChallengeId";
    private static final String FIELD_EMAIL_VERIFIED = "emailVerified";
    private static final String FIELD_PHONE_VERIFIED = "phoneVerified";
    private static final String FIELD_IDENTITY_VERIFIED = "identityVerified";
    private static final String FIELD_REQUIRED_CONSENTED = "requiredConsented";
    private static final String FIELD_VERIFICATION_REF = "verificationRef";
    private static final String FIELD_CI_HASH = "ciHash";
    private static final String FIELD_CONSENTS = "consents";
    private static final String MARKED = "1";

    private final StringRedisTemplate redis;
    private final RedisScript<Long> createScript;
    private final RedisScript<Long> markScript;

    public RedisRegistrationSessionStore(
            StringRedisTemplate redis,
            @Qualifier("createRegistrationScript") RedisScript<Long> createRegistrationScript,
            @Qualifier("markRegistrationScript") RedisScript<Long> markRegistrationScript) {
        this.redis = redis;
        this.createScript = createRegistrationScript;
        this.markScript = markRegistrationScript;
    }

    @Override
    public void create(UUID registrationId, RegistrationType type, String tokenHash, String loginEmail, Duration ttl) {
        try {
            redis.execute(
                    createScript,
                    List.of(registrationKey(registrationId)),
                    Long.toString(ttl.toMillis()),
                    FIELD_TYPE,
                    type.name(),
                    FIELD_TOKEN_HASH,
                    tokenHash,
                    FIELD_LOGIN_EMAIL,
                    loginEmail);
        } catch (DataAccessException e) {
            log.warn("온보딩 세션 생성 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    @Override
    public Optional<RegistrationSnapshot> find(UUID registrationId) {
        Map<String, String> fields;
        try {
            fields = redis.<String, String>opsForHash().entries(registrationKey(registrationId));
        } catch (DataAccessException e) {
            log.warn("온보딩 세션 조회 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toSnapshot(fields));
    }

    @Override
    public boolean attachEmailChallenge(UUID registrationId, String challengeId) {
        return mark(registrationId, FIELD_EMAIL_CHALLENGE_ID, challengeId);
    }

    @Override
    public boolean attachPhoneChallenge(UUID registrationId, String challengeId) {
        return mark(registrationId, FIELD_PHONE_CHALLENGE_ID, challengeId);
    }

    @Override
    public boolean markEmailVerified(UUID registrationId) {
        return mark(registrationId, FIELD_EMAIL_VERIFIED, MARKED);
    }

    @Override
    public boolean markPhoneVerified(UUID registrationId) {
        return mark(registrationId, FIELD_PHONE_VERIFIED, MARKED);
    }

    @Override
    public boolean markIdentityVerified(UUID registrationId, UUID verificationRef, String ciHash) {
        return mark(
                registrationId,
                FIELD_IDENTITY_VERIFIED,
                MARKED,
                FIELD_VERIFICATION_REF,
                verificationRef.toString(),
                FIELD_CI_HASH,
                ciHash);
    }

    @Override
    public boolean markRequiredConsented(UUID registrationId, List<ConsentSelection> consents) {
        return mark(registrationId, FIELD_REQUIRED_CONSENTED, MARKED, FIELD_CONSENTS, serializeConsents(consents));
    }

    private boolean mark(UUID registrationId, String... fieldValuePairs) {
        try {
            Long result =
                    redis.execute(markScript, List.of(registrationKey(registrationId)), (Object[]) fieldValuePairs);
            return result != null && result == 1L;
        } catch (DataAccessException e) {
            log.warn("온보딩 세션 마킹 중 Redis 예외 — fail-closed(503)", e);
            throw new AuthException(AuthErrorCode.SESSION_STORE_UNAVAILABLE);
        }
    }

    private static RegistrationSnapshot toSnapshot(Map<String, String> fields) {
        Set<RegistrationStep> completed = EnumSet.noneOf(RegistrationStep.class);
        if (MARKED.equals(fields.get(FIELD_EMAIL_VERIFIED))) {
            completed.add(RegistrationStep.EMAIL_VERIFIED);
        }
        if (MARKED.equals(fields.get(FIELD_PHONE_VERIFIED))) {
            completed.add(RegistrationStep.PHONE_VERIFIED);
        }
        if (MARKED.equals(fields.get(FIELD_IDENTITY_VERIFIED))) {
            completed.add(RegistrationStep.IDENTITY_VERIFIED);
        }
        if (MARKED.equals(fields.get(FIELD_REQUIRED_CONSENTED))) {
            completed.add(RegistrationStep.REQUIRED_CONSENTED);
        }
        String verificationRef = fields.get(FIELD_VERIFICATION_REF);
        return new RegistrationSnapshot(
                RegistrationType.valueOf(requireField(fields, FIELD_TYPE)),
                requireField(fields, FIELD_TOKEN_HASH),
                requireField(fields, FIELD_LOGIN_EMAIL),
                completed,
                fields.get(FIELD_EMAIL_CHALLENGE_ID),
                fields.get(FIELD_PHONE_CHALLENGE_ID),
                verificationRef == null ? null : UUID.fromString(verificationRef),
                fields.get(FIELD_CI_HASH),
                parseConsents(fields.get(FIELD_CONSENTS)));
    }

    private static String requireField(Map<String, String> fields, String field) {
        String value = fields.get(field);
        if (value == null) {
            throw new IllegalStateException("온보딩 세션 hash에 필수 필드가 없습니다: " + field);
        }
        return value;
    }

    private static String serializeConsents(List<ConsentSelection> consents) {
        return consents.stream()
                .map(consent -> consent.termsType() + ":" + consent.termsVersion())
                .collect(Collectors.joining(","));
    }

    private static List<ConsentSelection> parseConsents(@Nullable String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return List.of();
        }
        List<ConsentSelection> consents = new ArrayList<>();
        for (String token : serialized.split(",", -1)) {
            String[] parts = token.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("온보딩 동의 버퍼 직렬화가 손상되었습니다: " + token);
            }
            consents.add(new ConsentSelection(parts[0], Integer.parseInt(parts[1])));
        }
        return List.copyOf(consents);
    }

    private static String registrationKey(UUID registrationId) {
        return "reg:" + registrationId;
    }
}
