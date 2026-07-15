package com.example.auth.common.messaging;

import java.time.Instant;
import java.util.UUID;

/**
 * 통합 이벤트 공개 스키마 규약이다(DOMAIN_MODEL.md "도메인 이벤트" 표의 굵은 이벤트가 구현).
 *
 * <p>통합 이벤트는 서비스 간 seam 계약으로, JSON 직렬화 가능한 불변 record여야 한다. 페이로드 필드는 공개
 * 스키마의 일부이므로 하위 호환을 깨는 변경(필드 삭제·의미 변경) 시 {@link #schemaVersion()}을 올린다.
 */
public interface IntegrationEvent {

    /**
     * 발생 1회당 고유한 이벤트 식별자를 반환한다(UUIDv7). 중복 전달 판정(디둡)의 키이므로 재전달·재시도에도
     * 값이 보존되도록 페이로드에 포함해야 한다.
     */
    UUID eventId();

    /**
     * 이벤트가 발생한 시각을 반환한다(발행·전달 시각이 아니다).
     */
    Instant occurredAt();

    /**
     * 이 이벤트 타입의 공개 스키마 버전을 반환한다. 타입별로 1부터 시작해 스키마가 진화할 때마다 단조 증가한다.
     */
    default int schemaVersion() {
        return 1;
    }

    /**
     * 공개 논리 타입명을 반환한다(관측·DLQ 귀속용). 기본값은 구현 record의 단순 클래스명이다.
     */
    default String eventType() {
        return getClass().getSimpleName();
    }
}
