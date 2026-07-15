package com.example.auth.domain.user.port;

import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import java.time.LocalDate;

/**
 * 본인확인기관 실명확인의 판정 결과다.
 *
 * <p>{@link Verified}의 필드는 요청 에코가 아니라 <b>기관 권위값</b>이다(저장 정본). {@code ci}·{@code di}
 * 원문은 이 결과 객체 수명 안에서만 존재해야 한다 — 영속은 해시({@code ciHash})·암호화(di)로만 한다.
 */
public sealed interface IdentityProviderOutcome {

    /**
     * 실명확인 성공 — 기관이 확인한 정체성과 CI/DI 원문.
     */
    record Verified(
            String name, LocalDate birthDate, Gender gender, Carrier carrier, String phone, String ci, String di)
            implements IdentityProviderOutcome {}

    /**
     * 실명확인 실패.
     */
    record Failed(String reason) implements IdentityProviderOutcome {}
}
