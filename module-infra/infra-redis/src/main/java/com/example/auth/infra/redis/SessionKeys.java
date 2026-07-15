package com.example.auth.infra.redis;

import java.util.UUID;

/**
 * 세션 애그리거트의 Redis 키 스키마를 소유한다.
 *
 * <p>세션·인덱스·유예 키는 모두 {@code {u:userId}} 해시태그를 담아 한 사용자의 애그리거트 전체가 단일
 * Cluster 슬롯에 놓인다 — 생성(상한 축출)·회전 Lua가 KEYS와 내부 DEL 루프로 만지는 모든 키의 원자성을
 * Cluster에서도 보존한다. {@code refidx}는 {@code jti→userId}를 resolve해야 해 태그를 담을 수 없으므로
 * 의도적으로 슬롯 밖이며 Lua 원자 경계에 넣지 않는다(단일 키 GET/SET).
 */
final class SessionKeys {

    private SessionKeys() {}

    static String tag(UUID userId) {
        return "{u:" + userId + "}";
    }

    static String sessionPrefix(UUID userId) {
        return "sess:" + tag(userId) + ":";
    }

    static String sessionKey(UUID userId, UUID sessionId) {
        return sessionPrefix(userId) + sessionId;
    }

    static String indexKey(UUID userId) {
        return "sessidx:" + tag(userId);
    }

    static String gracePrefix(UUID userId) {
        return "grace:" + tag(userId) + ":";
    }

    static String refIndexKey(String jtiHash) {
        return "refidx:" + jtiHash;
    }
}
