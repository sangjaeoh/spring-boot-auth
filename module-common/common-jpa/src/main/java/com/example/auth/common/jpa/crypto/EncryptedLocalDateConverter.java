package com.example.auth.common.jpa.crypto;

import com.example.auth.common.core.crypto.EnvelopeCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.jspecify.annotations.Nullable;

/**
 * PII 날짜 컬럼(생년월일 등)을 ISO 문자열로 봉투 암호화 저장한다.
 *
 * <p>암호화하므로 컬럼은 {@code DATE}가 아니라 {@code VARCHAR}로 매핑된다(범위·정렬 질의 불가 — 암호화 PII의
 * 의도된 대가). 주입·생명주기는 {@link EncryptedStringConverter}와 같다({@code EnvelopeCipher} 빈 주입).
 */
@Converter
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private final EnvelopeCipher cipher;

    public EncryptedLocalDateConverter(EnvelopeCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable LocalDate attribute) {
        return attribute == null ? null : cipher.encrypt(attribute.format(DateTimeFormatter.ISO_LOCAL_DATE));
    }

    @Override
    public @Nullable LocalDate convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : LocalDate.parse(cipher.decrypt(dbData), DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
