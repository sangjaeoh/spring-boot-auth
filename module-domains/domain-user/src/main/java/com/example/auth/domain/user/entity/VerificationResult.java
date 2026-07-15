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
 * 본인인증 완료 결과 값 객체다(VERIFIED에서만 존재, 완료 후 불변).
 *
 * <p>실명·생년월일·휴대폰·DI는 <b>암호화 저장</b>한다(DOMAIN_MODEL 영속 유의절). {@code ciHash}는 원문
 * CI가 아니라 salted HMAC(pepper 버전 동봉)만 보관한다 — 원문 CI는 어디에도 저장되지 않는다.
 */
@Embeddable
public record VerificationResult(
        @Convert(converter = EncryptedStringConverter.class) @Column(name = "name", length = 512)
        String name,

        @Convert(converter = EncryptedLocalDateConverter.class) @Column(name = "birth_date", length = 512)
        LocalDate birthDate,

        @Enumerated(EnumType.STRING) @Column(name = "gender", length = 10)
        Gender gender,

        @Enumerated(EnumType.STRING) @Column(name = "carrier", length = 10)
        Carrier carrier,

        @Convert(converter = EncryptedStringConverter.class) @Column(name = "phone", length = 512)
        String phone,

        @Column(name = "ci_hash", length = 128) String ciHash,

        @Convert(converter = EncryptedStringConverter.class) @Column(name = "di", length = 512)
        String di) {

    /**
     * 기관 권위값과 ciHash로 완료 결과 값을 만든다.
     */
    public static VerificationResult of(
            String name, LocalDate birthDate, Gender gender, Carrier carrier, String phone, String ciHash, String di) {
        return new VerificationResult(name, birthDate, gender, carrier, phone, ciHash, di);
    }
}
