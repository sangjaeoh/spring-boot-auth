package com.example.auth.external.social;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 애플 토큰 엔드포인트용 동적 {@code client_secret}(ES256 JWT)을 .p8 개발자 키로 서명 생성한다.
 *
 * <p>애플은 고정 시크릿 대신 팀 키(.p8)로 서명한 단기 JWT를 요구한다(iss=teamId, sub=clientId,
 * aud=애플 발급자, kid=keyId). 인가코드 교환·토큰 폐기 등 서버-애플 왕복의 재료이며, {@code id_token}
 * 검증({@link OidcIdTokenVerifier})은 공개 JWKS만 쓰므로 이 시크릿이 필요 없다. 소비 지점(코드 교환
 * 경로)은 Apple Developer 조달 완료 시 설정 주입과 함께 배선한다.
 */
public class AppleClientSecretFactory {

    private static final String APPLE_AUDIENCE = "https://appleid.apple.com";
    private static final Duration MAX_TTL = Duration.ofDays(180);

    private final String teamId;
    private final String keyId;
    private final String clientId;
    private final ECPrivateKey privateKey;

    /**
     * .p8 파일 내용(PKCS#8 PEM)으로 팩토리를 생성한다.
     *
     * @throws IllegalArgumentException 키가 PKCS#8 EC 개인키가 아니면
     */
    public AppleClientSecretFactory(String teamId, String keyId, String clientId, String privateKeyPem) {
        this.teamId = teamId;
        this.keyId = keyId;
        this.clientId = clientId;
        this.privateKey = parsePrivateKey(privateKeyPem);
    }

    /**
     * 서명된 {@code client_secret} JWT를 반환한다. TTL은 애플 상한(6개월) 이내여야 한다.
     *
     * @throws IllegalArgumentException TTL이 상한을 넘으면
     */
    public String generate(Instant now, Duration ttl) {
        if (ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("애플 client_secret TTL 상한(180일)을 초과했습니다: " + ttl);
        }
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(teamId)
                .subject(clientId)
                .audience(APPLE_AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("애플 client_secret 서명에 실패했습니다.", e);
        }
        return jwt.serialize();
    }

    private static ECPrivateKey parsePrivateKey(String privateKeyPem) {
        String base64 = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException | ClassCastException e) {
            throw new IllegalArgumentException("애플 .p8 개인키(PKCS#8 EC PEM)를 해석할 수 없습니다.", e);
        }
    }
}
