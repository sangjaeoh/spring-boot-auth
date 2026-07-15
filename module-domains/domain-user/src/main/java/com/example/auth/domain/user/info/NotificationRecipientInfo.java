package com.example.auth.domain.user.info;

import com.example.auth.domain.user.entity.Contact;

/**
 * 알림 수신자 연락처(원문)의 경계 조회 모델이다 — 이메일/SMS 발송 대상 주소의 진실원본
 * (인증 {@code loginEmail} 미사용).
 */
public record NotificationRecipientInfo(String contactEmail, String contactPhone) {

    public static NotificationRecipientInfo from(Contact contact) {
        return new NotificationRecipientInfo(
                contact.contactEmail().value(), contact.contactPhone().number());
    }
}
