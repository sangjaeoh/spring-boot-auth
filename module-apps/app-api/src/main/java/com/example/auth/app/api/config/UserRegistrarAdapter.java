package com.example.auth.app.api.config;

import com.example.auth.domain.auth.entity.ConsentSelection;
import com.example.auth.domain.auth.port.UserCreationRequest;
import com.example.auth.domain.auth.port.UserRegistrar;
import com.example.auth.domain.user.entity.TermsType;
import com.example.auth.domain.user.service.UserRegistrationProcessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 인증의 {@code CreateUser} 포트를 유저 도메인 서비스에 연결하는 앱 어댑터다.
 *
 * <p>인증 도메인은 유저 도메인에 의존하지 않으므로 크로스 seam 배선은 앱이 제공한다. 인증이 opaque
 * 문자열로 버퍼링한 약관 유형을 유저 소유 enum으로 여기서 복원한다(버퍼 writer가 enum name이라 실패는
 * 호출자 버그다).
 */
@Component
public class UserRegistrarAdapter implements UserRegistrar {

    private final UserRegistrationProcessor userRegistrationProcessor;

    public UserRegistrarAdapter(UserRegistrationProcessor userRegistrationProcessor) {
        this.userRegistrationProcessor = userRegistrationProcessor;
    }

    @Override
    public UUID createUser(UserCreationRequest request) {
        Map<TermsType, Integer> consents = new LinkedHashMap<>();
        for (ConsentSelection selection : request.consents()) {
            consents.put(TermsType.valueOf(selection.termsType()), selection.termsVersion());
        }
        return userRegistrationProcessor.register(
                request.verificationRef(), request.ciHash(), request.loginEmail(), consents);
    }
}
