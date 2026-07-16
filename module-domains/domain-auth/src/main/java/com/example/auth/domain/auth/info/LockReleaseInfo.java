package com.example.auth.domain.auth.info;

import com.example.auth.domain.auth.entity.LockReason;
import com.example.auth.domain.auth.entity.LockState;
import org.jspecify.annotations.Nullable;

/**
 * 잠금 해제 직전의 잠금 상태 스냅샷이다(관리자 해제의 감사 전/후 값 기록용 경계 모델).
 */
public record LockReleaseInfo(
        LockState previousLockState, @Nullable LockReason previousLockReason) {}
