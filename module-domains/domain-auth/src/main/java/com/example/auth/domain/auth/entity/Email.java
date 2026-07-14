package com.example.auth.domain.auth.entity;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 로그인 식별자 이메일 값 객체다(정규화·형식 검증).
 *
 * <p>소문자·trim 정규화를 생성 시 강제한다. auth 도메인이 소유하는 로그인 식별자(`loginEmail`)이며, 유저
 * 서비스의 연락용 이메일과는 별개 개념이다(크로스 seam 타입 미공유).
 */
public record Email(String value) {

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_LENGTH = 320;

    public Email {
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
    }

    /**
     * 원문을 정규화·검증한 이메일 값을 반환한다.
     */
    public static Email of(String raw) {
        return new Email(raw);
    }
}
