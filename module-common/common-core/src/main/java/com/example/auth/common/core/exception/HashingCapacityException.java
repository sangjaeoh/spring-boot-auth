package com.example.auth.common.core.exception;

/**
 * 해시 동시성 상한을 초과해 부하를 차단할 때 던진다(경계에서 503).
 *
 * <p>{@link com.example.auth.common.core.crypto.PasswordHasher} 구현이 용량 초과 시 던지는 백프레셔
 * 신호다. 계정·입력과 무관하게 발생하므로 열거·타이밍 신호가 아니다.
 */
public class HashingCapacityException extends BaseException {

    public HashingCapacityException() {
        super(CryptoErrorCode.HASHING_CAPACITY_EXCEEDED);
    }
}
