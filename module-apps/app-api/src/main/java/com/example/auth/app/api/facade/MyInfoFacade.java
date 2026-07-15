package com.example.auth.app.api.facade;

import com.example.auth.app.api.presentation.v1.MyInfoResponse;
import com.example.auth.common.core.masking.PiiMasker;
import com.example.auth.domain.auth.service.AuthAccountModifier;
import com.example.auth.domain.auth.service.AuthAccountReader;
import com.example.auth.domain.user.info.MyUserInfo;
import com.example.auth.domain.user.service.UserModifier;
import com.example.auth.domain.user.service.UserReader;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 내 정보 조회·수정을 조율한다(트랜잭션은 각 도메인 서비스가 소유 — 파사드는 열지 않는다).
 *
 * <p>회원 정보(유저)와 로그인 이메일(인증)을 합성해 마스킹 적용된 단일 뷰를 만든다. 프로필(이름·생년·
 * 성별·휴대폰)은 본인인증 결과로만 채워지는 불변이라 수정 경로가 없다.
 */
@Component
public class MyInfoFacade {

    private final UserReader userReader;
    private final UserModifier userModifier;
    private final AuthAccountReader authAccountReader;
    private final AuthAccountModifier authAccountModifier;

    public MyInfoFacade(
            UserReader userReader,
            UserModifier userModifier,
            AuthAccountReader authAccountReader,
            AuthAccountModifier authAccountModifier) {
        this.userReader = userReader;
        this.userModifier = userModifier;
        this.authAccountReader = authAccountReader;
        this.authAccountModifier = authAccountModifier;
    }

    /**
     * 마스킹 적용된 내 정보(프로필·연락처·로그인 이메일·상태)를 반환한다.
     */
    public MyInfoResponse me(UUID userId) {
        MyUserInfo info = userReader.getMe(userId);
        String maskedLoginEmail = PiiMasker.maskEmail(authAccountReader.getLoginEmail(userId));
        return MyInfoResponse.of(info, maskedLoginEmail);
    }

    /**
     * 연락용 이메일을 변경한다(로그인 식별자와 독립).
     */
    public void changeContactEmail(UUID userId, String newEmail) {
        userModifier.changeContactEmail(userId, newEmail);
    }

    /**
     * 로그인 이메일을 변경한다(유니크, 연락용 이메일과 독립).
     */
    public void changeLoginEmail(UUID userId, String newEmail) {
        authAccountModifier.changeLoginEmail(userId, newEmail);
    }
}
