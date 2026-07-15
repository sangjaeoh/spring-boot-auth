package com.example.auth.external.social;

import com.example.auth.domain.auth.entity.SocialProvider;
import com.example.auth.domain.auth.port.SocialIdentityOutcome;
import com.example.auth.domain.auth.port.SocialIdentityProvider;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 실 4사(카카오·네이버·구글·애플) OIDC {@code id_token} 검증 어댑터다({@code auth.social.mode=oidc}).
 *
 * <p>발급자·JWKS URI는 각사 게시값을 기본으로 두고, {@code client-id}(aud)는 앱 등록 조달 완료 시
 * 설정으로 주입한다 — client-id가 비어 있는 제공자는 미설정으로 간주해 검증을 거부한다(fail-closed).
 * 네이버 JWKS URI는 파트너 문서 확인 후 설정으로 채운다(기본 공란).
 *
 * <p>애플 특수사항 중 토큰 엔드포인트용 동적 client_secret(.p8 서명)은 {@link AppleClientSecretFactory}가
 * 소유한다 — {@code id_token} 검증 자체는 공개 JWKS만 필요하다. 사용자 정보(이름)는 최초 인가 1회만
 * 제공되나 서버 검증 재료는 매 로그인 {@code id_token}에 있다(subject·email·릴레이 플래그).
 */
@Component
@ConditionalOnProperty(name = "auth.social.mode", havingValue = "oidc")
public class OidcSocialIdentityProvider implements SocialIdentityProvider {

    private final Map<SocialProvider, OidcIdTokenVerifier> verifiers = new EnumMap<>(SocialProvider.class);

    public OidcSocialIdentityProvider(
            @Value("${auth.social.oidc.kakao.issuer:https://kauth.kakao.com}") String kakaoIssuer,
            @Value("${auth.social.oidc.kakao.jwks-uri:https://kauth.kakao.com/.well-known/jwks.json}")
                    String kakaoJwksUri,
            @Value("${auth.social.oidc.kakao.client-id:}") String kakaoClientId,
            @Value("${auth.social.oidc.naver.issuer:https://nid.naver.com}") String naverIssuer,
            @Value("${auth.social.oidc.naver.jwks-uri:}") String naverJwksUri,
            @Value("${auth.social.oidc.naver.client-id:}") String naverClientId,
            @Value("${auth.social.oidc.google.issuer:https://accounts.google.com}") String googleIssuer,
            @Value("${auth.social.oidc.google.jwks-uri:https://www.googleapis.com/oauth2/v3/certs}")
                    String googleJwksUri,
            @Value("${auth.social.oidc.google.client-id:}") String googleClientId,
            @Value("${auth.social.oidc.apple.issuer:https://appleid.apple.com}") String appleIssuer,
            @Value("${auth.social.oidc.apple.jwks-uri:https://appleid.apple.com/auth/keys}") String appleJwksUri,
            @Value("${auth.social.oidc.apple.client-id:}") String appleClientId) {
        registerIfConfigured(SocialProvider.KAKAO, kakaoIssuer, kakaoJwksUri, kakaoClientId);
        registerIfConfigured(SocialProvider.NAVER, naverIssuer, naverJwksUri, naverClientId);
        registerIfConfigured(SocialProvider.GOOGLE, googleIssuer, googleJwksUri, googleClientId);
        registerIfConfigured(SocialProvider.APPLE, appleIssuer, appleJwksUri, appleClientId);
    }

    @Override
    public SocialIdentityOutcome verify(SocialProvider provider, String idToken) {
        OidcIdTokenVerifier verifier = verifiers.get(provider);
        if (verifier == null) {
            return new SocialIdentityOutcome.Failed("미설정 제공자: " + provider);
        }
        return verifier.verify(idToken);
    }

    private void registerIfConfigured(SocialProvider provider, String issuer, String jwksUri, String clientId) {
        if (clientId.isBlank() || jwksUri.isBlank()) {
            return;
        }
        verifiers.put(provider, OidcIdTokenVerifier.forRemoteJwks(issuer, clientId, jwksUri));
    }
}
