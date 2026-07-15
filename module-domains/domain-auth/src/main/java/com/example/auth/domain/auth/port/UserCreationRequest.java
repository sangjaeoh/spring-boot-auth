package com.example.auth.domain.auth.port;

import com.example.auth.domain.auth.entity.ConsentSelection;
import java.util.List;
import java.util.UUID;

/**
 * 유저 서비스에 보내는 단일 {@code CreateUser} 명령의 재료다(DOMAIN_MODEL §2.8 굵은 명령 —
 * 유저 애그리거트를 seam 너머로 개별 조작하지 않는다).
 */
public record UserCreationRequest(
        UUID verificationRef, String ciHash, String loginEmail, List<ConsentSelection> consents) {

    public UserCreationRequest {
        consents = List.copyOf(consents);
    }
}
