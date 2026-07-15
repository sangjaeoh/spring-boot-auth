package com.example.auth.external.identity;

import com.example.auth.domain.user.entity.Provider;
import com.example.auth.domain.user.port.IdentityProviderOutcome;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.port.IdentityVerificationProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 외부 호출 없는 Mock 본인인증 어댑터다(dev/test 오프라인 E2E용). 항상 성공을 반환한다.
 *
 * <p>CI/DI는 주장된 정체성(실명·생년월일·휴대폰)에서 <b>결정적으로</b> 파생한다 — 같은 사람의 재시도는
 * 같은 CI를 얻어 CI 원장 유일성 시맨틱이 dev에서도 실기관처럼 동작한다. 생년월일은 ISO-8601
 * ({@code LocalDate#toString()})로 정규화해 파생 입력을 고정한다. 실 기관 어댑터는 P4에서 이 포트를
 * 교체 구현한다.
 */
@Component
public class MockIdentityVerificationProvider implements IdentityVerificationProvider {

    @Override
    public Provider provider() {
        return Provider.MOCK;
    }

    @Override
    public IdentityProviderOutcome verify(IdentityProviderRequest request) {
        String identity = request.name() + "|" + request.birthDate() + "|" + request.phone();
        return new IdentityProviderOutcome.Verified(
                request.name(),
                request.birthDate(),
                request.gender(),
                request.carrier(),
                request.phone(),
                deterministicDigest("mock-ci|" + identity),
                deterministicDigest("mock-di|" + identity));
    }

    private static String deterministicDigest(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256은 모든 JVM에 존재해야 한다", e);
        }
    }
}
