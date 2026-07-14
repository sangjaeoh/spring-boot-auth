package com.example.auth.domain.auth.event;

import com.example.auth.domain.auth.entity.FailureReason;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 실패 도메인 이벤트다({@code userId}는 계정 식별 실패 시 null).
 */
public record LoginFailed(@Nullable UUID userId, FailureReason reason, Instant at) {}
