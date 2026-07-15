package com.example.auth.common.core.exception;

/**
 * 암호 원자재(해시 등) 실행의 에러 코드다.
 *
 * <p>해시 동시성 상한 초과는 계정·입력과 무관한 용량 신호이므로 단일 503으로 노출한다(부하 차단).
 */
public enum CryptoErrorCode implements ErrorCode {
    HASHING_CAPACITY_EXCEEDED("CRYPTO_HASHING_CAPACITY_EXCEEDED", "일시적으로 요청이 많아 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", 503);

    private final String code;
    private final String message;
    private final int status;

    CryptoErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int status() {
        return status;
    }
}
