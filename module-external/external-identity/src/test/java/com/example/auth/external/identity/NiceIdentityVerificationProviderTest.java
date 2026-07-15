package com.example.auth.external.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.auth.domain.user.entity.Carrier;
import com.example.auth.domain.user.entity.Gender;
import com.example.auth.domain.user.port.IdentityProviderOutcome;
import com.example.auth.domain.user.port.IdentityProviderRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * NICE 어댑터 골격의 요청 구성·판정 매핑·오류 시맨틱을 검증한다(실 계약 페이로드는 조달 완료 후 교체).
 */
class NiceIdentityVerificationProviderTest {

    private static final String BASE_URL = "https://nice.test";

    private MockRestServiceServer server;
    private NiceIdentityVerificationProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new NiceIdentityVerificationProvider(builder.build(), "SITE");
    }

    @Test
    @DisplayName("성공 응답은 기관 응답값을 정본으로 Verified에 매핑한다")
    void mapsSuccessToVerified() {
        server.expect(requestTo(BASE_URL + "/identity/v1/verifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.siteCode").value("SITE"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andRespond(withSuccess("""
                        {"resultCode":"0000","name":"홍길동","birthDate":"1990-01-01","gender":"MALE",
                         "carrier":"SKT","phone":"+821012345678","ci":"ci-value","di":"di-value"}
                        """, MediaType.APPLICATION_JSON));

        IdentityProviderOutcome outcome = provider.verify(request());

        assertThat(outcome).isInstanceOf(IdentityProviderOutcome.Verified.class);
        IdentityProviderOutcome.Verified verified = (IdentityProviderOutcome.Verified) outcome;
        assertThat(verified.name()).isEqualTo("홍길동");
        assertThat(verified.birthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(verified.ci()).isEqualTo("ci-value");
        assertThat(verified.di()).isEqualTo("di-value");
    }

    @Test
    @DisplayName("기관 판정 실패 응답은 Failed로 매핑한다")
    void mapsRejectionToFailed() {
        server.expect(requestTo(BASE_URL + "/identity/v1/verifications"))
                .andRespond(withSuccess("""
                        {"resultCode":"1001","message":"명의 불일치"}
                        """, MediaType.APPLICATION_JSON));

        IdentityProviderOutcome outcome = provider.verify(request());

        assertThat(outcome).isInstanceOf(IdentityProviderOutcome.Failed.class);
        assertThat(((IdentityProviderOutcome.Failed) outcome).reason()).isEqualTo("명의 불일치");
    }

    @Test
    @DisplayName("통신 장애·비2xx는 런타임 예외로 전파한다(FAILED 오분류 금지 — EXPIRED 스윕이 수렴)")
    void propagatesTransportFailure() {
        server.expect(requestTo(BASE_URL + "/identity/v1/verifications")).andRespond(withServerError());

        assertThatThrownBy(() -> provider.verify(request())).isInstanceOf(RestClientException.class);
    }

    private static IdentityProviderRequest request() {
        return new IdentityProviderRequest("홍길동", LocalDate.of(1990, 1, 1), Gender.MALE, Carrier.SKT, "+821012345678");
    }
}
