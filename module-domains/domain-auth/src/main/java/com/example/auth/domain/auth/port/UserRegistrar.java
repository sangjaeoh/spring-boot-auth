package com.example.auth.domain.auth.port;

import java.util.UUID;

/**
 * 유저 서비스의 정회원 생성({@code CreateUser}) 포트다. 인증 도메인은 유저 도메인에 컴파일 의존하지
 * 않으므로 앱이 유저 도메인 서비스로 어댑트한다.
 */
public interface UserRegistrar {

    /**
     * 활성 회원을 원자 생성하고 {@code UserId}를 반환한다. 멱등하다 — 같은 {@code verificationRef}로
     * 이미 생성된 회원이 있으면 신규 쓰기 없이 그 {@code UserId}를 재반환한다.
     *
     * <p>호출 트랜잭션에 참여한다(단일 PostgreSQL 전제 — 가입 완료 단일 트랜잭션의 구성 요소).
     */
    UUID createUser(UserCreationRequest request);
}
