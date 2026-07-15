package com.example.auth.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

/**
 * 연락처 값 객체다(알림 수신자 진실원본). 연락용 이메일은 평문, 휴대폰은 암호화한다.
 *
 * <p>{@code contactEmail}은 가입 시 인증의 {@code loginEmail}로 seed되나 독립 변경 가능한 별개 개념이다.
 */
@Embeddable
public record Contact(
        @Convert(converter = EmailConverter.class) @Column(name = "contact_email", length = 320)
        Email contactEmail,

        @Embedded PhoneNumber contactPhone) {

    /**
     * 연락용 이메일과 휴대폰으로 연락처 값을 만든다.
     */
    public static Contact of(Email contactEmail, PhoneNumber contactPhone) {
        return new Contact(contactEmail, contactPhone);
    }
}
