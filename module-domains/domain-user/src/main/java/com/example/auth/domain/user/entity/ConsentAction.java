package com.example.auth.domain.user.entity;

/**
 * 동의 이력 액션이다(DOMAIN_MODEL §1.5). WITHDRAW는 선택 약관에만 허용된다(필수 철회는 탈퇴 경로 — P4).
 */
public enum ConsentAction {
    AGREE,
    WITHDRAW
}
