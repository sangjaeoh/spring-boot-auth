package com.example.auth.common.auth.jwt;

import java.time.Instant;

/**
 * 스토어에 영속되는 서명키 한 벌이다.
 *
 * <p>{@code privateJwkJson}은 개인키를 포함한 JWK JSON 평문이다 — 저장 시 암호화(봉투)는 스토어 구현이
 * 소유하고, 이 경계에서는 항상 평문으로 오간다.
 */
public record StoredSigningKey(String kid, String privateJwkJson, Instant createdAt) {}
