package com.example.auth.domain.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/**
 * {@link Email} VO를 단일 varchar 컬럼으로 매핑한다(평문 — 암호화 대상 아님).
 */
@Converter
public class EmailConverter implements AttributeConverter<Email, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Email attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public @Nullable Email convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : Email.of(dbData);
    }
}
