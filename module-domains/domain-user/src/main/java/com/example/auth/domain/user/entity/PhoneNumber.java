package com.example.auth.domain.user.entity;

import com.example.auth.common.jpa.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.regex.Pattern;

/**
 * 휴대폰 값 객체다(본인인증 결과). 번호는 <b>E.164 형식</b>({@code +국가코드})이어야 하며 암호화 저장한다.
 *
 * <p>정규화(표현 통일)는 upstream(본인인증 결과 매핑)이 소유한다 — VO는 형식을 <b>검증</b>하되 표현을 변형하지
 * 않는다. blind index 동등조회가 이 형식 일관성에 의존하므로, 비-E.164 입력은 조용한 오조회 대신 즉시 거부한다.
 * 동등 조회는 암호문으로 불가하므로 조회가 필요하면 {@code User}가 별도 blind index 컬럼을 든다(파생값이라 VO
 * 밖). 통신사는 평문 enum이다.
 */
@Embeddable
public record PhoneNumber(
        @Enumerated(EnumType.STRING) @Column(name = "carrier", length = 10)
        Carrier carrier,

        @Convert(converter = EncryptedStringConverter.class) @Column(name = "contact_phone", length = 512)
        String number) {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{1,14}$");

    public PhoneNumber {
        if (!E164.matcher(number).matches()) {
            throw new IllegalArgumentException("전화번호는 E.164 형식(+국가코드)이어야 합니다.");
        }
    }

    /**
     * 통신사와 E.164 번호로 휴대폰 값을 만든다.
     */
    public static PhoneNumber of(Carrier carrier, String number) {
        return new PhoneNumber(carrier, number);
    }
}
