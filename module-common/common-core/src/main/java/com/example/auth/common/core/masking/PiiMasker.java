package com.example.auth.common.core.masking;

/**
 * PII 표시 마스킹 정책이다(내 정보·관리자 조회 공용).
 *
 * <p>저장값이 아니라 출력 표현만 다룬다 — 입력을 검증하지 않고 형식이 어긋나면 가능한 만큼 마스킹한다
 * (마스킹 실패로 원문이 새는 것보다 과마스킹이 안전측).
 */
public final class PiiMasker {

    private PiiMasker() {}

    /**
     * 실명을 마스킹한다 — 첫 글자만 남긴다(한 글자면 전체 마스킹).
     */
    public static String maskName(String name) {
        int[] codePoints = name.codePoints().toArray();
        if (codePoints.length <= 1) {
            return "*";
        }
        return new String(codePoints, 0, 1) + "*".repeat(codePoints.length - 1);
    }

    /**
     * 이메일을 마스킹한다 — 로컬파트 앞 2자만 남기고 도메인은 유지한다.
     */
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }

    /**
     * 전화번호를 마스킹한다 — 앞 5자·뒤 4자만 남긴다(짧으면 뒤 4자 이전 전체 마스킹).
     */
    public static String maskPhone(String phone) {
        if (phone.length() <= 9) {
            return "*".repeat(Math.max(0, phone.length() - 4)) + phone.substring(Math.max(0, phone.length() - 4));
        }
        return phone.substring(0, 5) + "*".repeat(phone.length() - 9) + phone.substring(phone.length() - 4);
    }
}
