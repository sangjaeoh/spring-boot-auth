package com.example.auth.app.admin.config;

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
 * <p>관리자 앱은 가입 라우트를 노출하지 않지만, 컴포넌트 스캔이 조립하는 인증 도메인 서비스
 * ({@code AccountRegistrationProcessor})가 이 포트 빈을 요구한다(app-api·app-batch와 동일 배선).
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
