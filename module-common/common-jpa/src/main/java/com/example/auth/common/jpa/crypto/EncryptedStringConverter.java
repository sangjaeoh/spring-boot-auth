package com.example.auth.common.jpa.crypto;

import com.example.auth.common.core.crypto.EnvelopeCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/**
 * PII 문자열 컬럼을 봉투 암호화 저장한다({@code @Convert(EncryptedStringConverter.class)}).
 *
 * <p>Hibernate가 {@code SpringBeanContainer}로 이 컨버터를 생성하며 생성자의 {@link EnvelopeCipher}를 컨텍스트
 * 빈에서 주입한다(자신은 빈일 필요 없음 — JPA 규약상 fresh 인스턴스로 조립). 따라서 PII를 영속하는 컨텍스트는
 * {@code EnvelopeCipher} 빈을 반드시 둔다(앱=infra-crypto 구현, 도메인 테스트=fake). 저장 시 암호화·조회 시 복호.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final EnvelopeCipher cipher;

    public EncryptedStringConverter(EnvelopeCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable String attribute) {
        return attribute == null ? null : cipher.encrypt(attribute);
    }

    @Override
    public @Nullable String convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : cipher.decrypt(dbData);
    }
}
