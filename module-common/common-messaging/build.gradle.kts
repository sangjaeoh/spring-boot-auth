plugins {
    id("convention.common-module")
}

// common-messaging은 발행 포트(MessagePublisher)·통합 이벤트 공개 스키마 규약·멱등 소비자 계약을 소유한다.
// transport·디둡·DLQ 구현은 infra-messaging(docs/architecture.md — common 모듈 배치). 순수 계약만 담아
// 의존 제로를 유지한다(도메인·앱이 이 모듈만 보고 발행·소비한다).
