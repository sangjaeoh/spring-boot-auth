package com.example.auth.infra.crypto;

import com.example.auth.common.core.crypto.BlindIndexer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256(비밀 pepper)로 PII의 결정적 blind index를 만든다.
 *
 * <p>출력은 {@code "<pepperVersion>:<base64 HMAC>"} — 같은 입력은 같은 출력이라 동등 조회가 가능하다. 보안은
 * 전적으로 pepper 비밀성에 의존한다(대상 값은 열거 가능한 저엔트로피). pepper 버전을 동봉해, pepper 회전 시
 * 옛 버전 인덱스를 식별·재인덱싱하는 seam을 남긴다(회전 절차는 후속 워크스트림). pepper는 암호문 저장소와
 * 분리된 시크릿 매니저에 보관해야 한다(유출 시 역추론 가능).
 */
@Component
public class HmacBlindIndexer implements BlindIndexer {

    private static final String ALGORITHM = "HmacSHA256";

    private final Map<Integer, SecretKeySpec> peppers;
    private final int activeVersion;
    private final SecretKeySpec activePepper;

    public HmacBlindIndexer(
            @Value("${crypto.blind-index.active-version:1}") int activeVersion,
            @Value("${crypto.blind-index.peppers}") String peppersCsv) {
        this.peppers = parsePeppers(peppersCsv);
        this.activeVersion = activeVersion;
        SecretKeySpec pepper = peppers.get(activeVersion);
        if (pepper == null) {
            throw new IllegalStateException("crypto.blind-index.peppers에 활성 버전 " + activeVersion + " pepper가 없습니다.");
        }
        this.activePepper = pepper;
    }

    @Override
    public String blindIndex(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(activePepper);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return activeVersion + ":" + Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("blind index 생성 실패", e);
        }
    }

    private static Map<Integer, SecretKeySpec> parsePeppers(String csv) {
        Map<Integer, SecretKeySpec> map = new HashMap<>();
        for (String entry : csv.split(",", -1)) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("crypto.blind-index.peppers 형식 오류(version:base64 기대): " + entry);
            }
            byte[] pepperBytes = Base64.getDecoder().decode(parts[1].trim());
            // 보안이 전적으로 pepper 비밀성에 의존하므로 최소 32바이트(HMAC-SHA256 출력 폭)를 강제한다.
            if (pepperBytes.length < 32) {
                throw new IllegalStateException("pepper는 최소 32바이트여야 합니다(HMAC-SHA256). 실제=" + pepperBytes.length);
            }
            map.put(Integer.parseInt(parts[0].trim()), new SecretKeySpec(pepperBytes, ALGORITHM));
        }
        return map;
    }
}
