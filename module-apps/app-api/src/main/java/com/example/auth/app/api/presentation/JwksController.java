package com.example.auth.app.api.presentation;

import com.example.auth.common.auth.jwt.SigningKeyRing;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서명 공개키를 JWKS로 게시한다(표준 well-known, 비버전 — 버전별 {@code presentation/v{n}} 밖).
 *
 * <p>현재 키 + 유예 창 안의 직전 키를 공개 파라미터로만 노출한다(개인키 미노출). 인증 없이 접근 가능하며
 * 외부 검증자가 이 키로 Access 토큰 서명을 검증한다.
 */
@RestController
public class JwksController {

    private final SigningKeyRing signingKeyRing;

    public JwksController(SigningKeyRing signingKeyRing) {
        this.signingKeyRing = signingKeyRing;
    }

    /**
     * 게시 대상 공개키 집합을 JWKS JSON으로 반환한다.
     */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return signingKeyRing.publicJwks(Instant.now());
    }
}
