package com.example.auth.domain.user.entity;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 연락용 이메일 값 객체다(정규화·형식 검증).
 *
 * <p>유저 서비스가 소유하는 <b>연락용</b> 주소({@code contactEmail})다. 인증 서비스의 로그인 식별자
 * ({@code loginEmail})와는 소유·의미가 다른 별개 개념이라 타입을 공유하지 않는다(크로스 seam 격리). 평문
 * 저장이다(DOMAIN_MODEL 영속 유의절 — 암호화 대상은 name·birthDate·phone).
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
