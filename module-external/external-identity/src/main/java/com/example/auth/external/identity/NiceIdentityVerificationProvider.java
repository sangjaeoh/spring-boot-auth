package com.example.auth.external.identity;

import static java.util.Objects.requireNonNull;

import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.entity.Provider;
import com.example.auth.domain.user.port.IdentityProviderOutcome;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import com.example.auth.domain.user.port.IdentityVerificationProvider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 실 본인확인기관(NICE) 어댑터다({@code user.identity-verification.mode=nice}).
 *
 * <p>조달(기관 계약·사이트코드 발급) 미완 상태의 <b>골격</b>이다 — 포트 계약·설정 스위치·HTTP 배선·
 * 오류 시맨틱은 완성돼 있고, 요청/응답 페이로드 형상만 기관 표준 API 계약 확정 시 이 클래스 안에서
 * 교체한다(도메인 무변경). 기관 판정 실패(응답 수신)는 {@code Failed}로 매핑하고, 통신 장애·비2xx는
 * 런타임 예외로 전파한다 — 기관에선 완료됐을 수 있는 미확정 상태이므로 호출측이 FAILED로 오분류하지
 * 않고 REQUESTED로 남겨 EXPIRED 스윕이 수렴한다.
 */
@Component
@ConditionalOnProperty(name = "user.identity-verification.mode", havingValue = "nice")
public class NiceIdentityVerificationProvider implements IdentityVerificationProvider {

    private static final String VERIFY_PATH = "/identity/v1/verifications";
    private static final String SUCCESS_CODE = "0000";

    private final RestClient restClient;
    private final String siteCode;

    @Autowired
    public NiceIdentityVerificationProvider(
            @Value("${user.identity-verification.nice.base-url:https://svc.niceapi.co.kr}") String baseUrl,
            @Value("${user.identity-verification.nice.site-code:}") String siteCode,
            @Value("${user.identity-verification.nice.client-id:}") String clientId,
            @Value("${user.identity-verification.nice.client-secret:}") String clientSecret,
            @Value("${user.identity-verification.nice.timeout-ms:3000}") long timeoutMs) {
        this(buildRestClient(baseUrl, clientId, clientSecret, timeoutMs), siteCode);
        if (siteCode.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "user.identity-verification.mode=nice에는 nice.site-code·client-id·client-secret 설정이 필요하다");
        }
    }

    NiceIdentityVerificationProvider(RestClient restClient, String siteCode) {
        this.restClient = restClient;
        this.siteCode = siteCode;
    }

    @Override
    public Provider provider() {
        return Provider.NICE;
    }

    @Override
    public IdentityProviderOutcome verify(IdentityProviderRequest request) {
        VerifyResponse response = requireNonNull(
                restClient
                        .post()
                        .uri(VERIFY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(VerifyRequest.from(siteCode, request))
                        .retrieve()
                        .body(VerifyResponse.class),
                "기관 응답 본문이 비어 있다");
        if (!SUCCESS_CODE.equals(response.resultCode())) {
            return new IdentityProviderOutcome.Failed(
                    response.message() != null ? response.message() : "기관 판정 실패: " + response.resultCode());
        }
        // 기관 응답값이 정본이다 — 주장값을 되돌려주지 않고 기관이 확인한 정체성으로 결과를 만든다.
        return new IdentityProviderOutcome.Verified(
                requireNonNull(response.name()),
                LocalDate.parse(requireNonNull(response.birthDate())),
                Gender.valueOf(requireNonNull(response.gender())),
                Carrier.valueOf(requireNonNull(response.carrier())),
                requireNonNull(response.phone()),
                requireNonNull(response.ci()),
                requireNonNull(response.di()));
    }

    private static RestClient buildRestClient(String baseUrl, String clientId, String clientSecret, long timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBasicAuth(clientId, clientSecret))
                .build();
    }

    record VerifyRequest(String siteCode, String name, String birthDate, String gender, String carrier, String phone) {

        static VerifyRequest from(String siteCode, IdentityProviderRequest request) {
            return new VerifyRequest(
                    siteCode,
                    request.name(),
                    request.birthDate().toString(),
                    request.gender().name(),
                    request.carrier().name(),
                    request.phone());
        }
    }

    record VerifyResponse(
            String resultCode,
            @Nullable String name,
            @Nullable String birthDate,
            @Nullable String gender,
            @Nullable String carrier,
            @Nullable String phone,
            @Nullable String ci,
            @Nullable String di,
            @Nullable String message) {}
}
