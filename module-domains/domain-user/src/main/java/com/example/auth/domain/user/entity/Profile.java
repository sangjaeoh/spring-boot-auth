package com.example.auth.domain.user.entity;

import com.example.auth.common.jpa.crypto.EncryptedLocalDateConverter;
import com.example.auth.common.jpa.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;

/**
 * 회원 프로필 값 객체다(본인인증 결과로만 채움). 실명·생년월일은 <b>암호화 저장</b>, 성별은 평문 enum이다
 * (DOMAIN_MODEL 영속 유의절 — 암호화 대상은 name·birth_date·phone).
 */
@Embeddable
public record Profile(
        @Convert(converter = EncryptedStringConverter.class) @Column(name = "name", length = 512)
        String name,

        @Convert(converter = EncryptedLocalDateConverter.class) @Column(name = "birth_date", length = 512)
        LocalDate birthDate,

        @Enumerated(EnumType.STRING) @Column(name = "gender", length = 10)
        Gender gender) {

    /**
     * 실명·생년월일·성별로 프로필 값을 만든다.
     */
    public static Profile of(String name, LocalDate birthDate, Gender gender) {
        return new Profile(name, birthDate, gender);
    }
}
