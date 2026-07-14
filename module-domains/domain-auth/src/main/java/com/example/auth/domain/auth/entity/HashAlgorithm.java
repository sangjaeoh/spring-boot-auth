package com.example.auth.domain.auth.entity;

/**
 * 비밀번호 해시 알고리즘 식별자다(마이그레이션 대비).
 */
public enum HashAlgorithm {
    ARGON2ID,
    BCRYPT
}
