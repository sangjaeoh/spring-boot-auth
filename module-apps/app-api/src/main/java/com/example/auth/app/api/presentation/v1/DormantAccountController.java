package com.example.auth.app.api.presentation.v1;

import com.example.auth.app.api.facade.DormantAccountFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 휴면 해제 API(자격 검증 → OTP 발송 → 검증·해제). 휴면 계정은 로그인이 403으로 차단되므로 비인증
 * 경로다(자격 검증은 플로우 내부에서 수행).
 */
@RestController
@RequestMapping("/auth/dormant-release")
public class DormantAccountController {

    private final DormantAccountFacade dormantAccountFacade;

    public DormantAccountController(DormantAccountFacade dormantAccountFacade) {
        this.dormantAccountFacade = dormantAccountFacade;
    }

    /**
     * 자격 검증 후 연락용 이메일로 해제 OTP를 발송한다.
     */
    @PostMapping("/challenge")
    public DormantReleaseChallengeResponse challenge(@Valid @RequestBody DormantReleaseChallengeRequest request) {
        return dormantAccountFacade.startRelease(request.email(), request.password());
    }

    /**
     * OTP를 검증하고 휴면을 해제한다(직후 정상 로그인 가능).
     */
    @PostMapping("/verify")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody DormantReleaseVerifyRequest request) {
        dormantAccountFacade.completeRelease(request.challengeId(), request.code());
    }
}
