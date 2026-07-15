package com.example.auth.common.core.crypto;

/**
 * PII 평문을 봉투(envelope) 암호화로 저장·복원하는 벤더 중립 원자재다.
 *
 * <p>도메인 의미가 없는 커모디티 암호 원자재라 포트를 common-core가 소유하고 infra가 구현한다
 * (docs/architecture.md — infra vs external). 암호문은 자기서술적이어야 한다 — 복호에 필요한 키 버전을
 * 스스로 담아, 키 회전 시 기존 전 행을 재암호화하지 않고도 옛 버전으로 복호할 수 있어야 한다.
 */
public interface EnvelopeCipher {

    /**
     * 평문을 자기서술적 암호문 문자열(키 버전 동봉)로 암호화해 반환한다.
     */
    String encrypt(String plaintext);

    /**
     * 암호문을 그 안에 동봉된 키 버전으로 복호해 원래 평문을 반환한다.
     */
    String decrypt(String ciphertext);
}
