/**
 * common-messaging 포트의 in-process 구현 — 커밋 후 전달 transport + 멱등 소비(디둡)·DLQ 재시도.
 *
 * <p>디둡·DLQ 테이블은 전용 {@code msg} 스키마에 두고 이 모듈이 마이그레이션을 소유한다. 도메인 의미 없는
 * 범용 기술 데이터라 특정 도메인 스키마(auth·usr)에 두면 도메인 경계가 오염되므로, 스키마 소유 = 마이그레이션
 * 소유 원칙(docs/architecture.md)을 구현 모듈에 적용한다. 물리 분리 시 배포 단위마다 자기 DB에 msg 스키마를
 * 가져 그대로 따라간다.
 */
@NullMarked
package com.example.auth.infra.messaging;

import org.jspecify.annotations.NullMarked;
