package com.example.auth.external.notification;

import com.example.auth.domain.generic.entity.NotificationChannel;
import com.example.auth.domain.generic.port.NotificationSender;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 실 발송 벤더 어댑터다({@code generic.notification.mode=vendor}).
 *
 * <p>조달(발송 벤더 계약·키 발급) 미완 상태의 <b>골격</b>이다 — 포트 계약·설정 스위치·HTTP 배선·채널
 * 라우팅은 완성돼 있고, 요청 페이로드 형상만 벤더 API 계약 확정 시 이 클래스 안에서 교체한다(도메인
 * 무변경). 채널별 벤더: EMAIL=SMTP/SES 계열 HTTP API, SMS=발송대행 HTTP API, PUSH=FCM(HTTP v1 —
 * APNs는 FCM 중계로 시작하고 직접 연동 시 확장). 비2xx·통신 장애는 런타임 예외로 전파한다 — 호출측
 * (NotificationProcessor)이 FAILED 이력으로 흡수하고 재시도한다. 페이로드(수신자·렌더 데이터)는
 * 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "generic.notification.mode", havingValue = "vendor")
public class VendorNotificationSender implements NotificationSender {

    private final RestClient emailClient;
    private final RestClient smsClient;
    private final RestClient pushClient;
    private final String emailSender;
    private final String smsSenderNumber;

    @Autowired
    public VendorNotificationSender(
            @Value("${generic.notification.email.base-url:}") String emailBaseUrl,
            @Value("${generic.notification.email.api-key:}") String emailApiKey,
            @Value("${generic.notification.email.sender:}") String emailSender,
            @Value("${generic.notification.sms.base-url:}") String smsBaseUrl,
            @Value("${generic.notification.sms.api-key:}") String smsApiKey,
            @Value("${generic.notification.sms.sender-number:}") String smsSenderNumber,
            @Value("${generic.notification.push.base-url:}") String pushBaseUrl,
            @Value("${generic.notification.push.api-key:}") String pushApiKey,
            @Value("${generic.notification.timeout-ms:3000}") long timeoutMs) {
        requireConfigured(List.of(
                "generic.notification.email.base-url", emailBaseUrl,
                "generic.notification.email.api-key", emailApiKey,
                "generic.notification.email.sender", emailSender,
                "generic.notification.sms.base-url", smsBaseUrl,
                "generic.notification.sms.api-key", smsApiKey,
                "generic.notification.sms.sender-number", smsSenderNumber,
                "generic.notification.push.base-url", pushBaseUrl,
                "generic.notification.push.api-key", pushApiKey));
        this.emailClient = buildRestClient(emailBaseUrl, emailApiKey, timeoutMs);
        this.smsClient = buildRestClient(smsBaseUrl, smsApiKey, timeoutMs);
        this.pushClient = buildRestClient(pushBaseUrl, pushApiKey, timeoutMs);
        this.emailSender = emailSender;
        this.smsSenderNumber = smsSenderNumber;
    }

    VendorNotificationSender(RestClient emailClient, RestClient smsClient, RestClient pushClient) {
        this.emailClient = emailClient;
        this.smsClient = smsClient;
        this.pushClient = pushClient;
        this.emailSender = "noreply@example.com";
        this.smsSenderNumber = "+821000000000";
    }

    @Override
    public void send(NotificationChannel channel, String target, String templateId, String payload) {
        // 요청 형상은 벤더 표준 API 계약 확정 시 교체한다(골격 — 조달 완료 후 항목).
        switch (channel) {
            case EMAIL ->
                emailClient
                        .post()
                        .uri("/v1/messages/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new EmailRequest(emailSender, target, templateId, payload))
                        .retrieve()
                        .toBodilessEntity();
            case SMS ->
                smsClient
                        .post()
                        .uri("/v1/messages/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new SmsRequest(smsSenderNumber, target, templateId, payload))
                        .retrieve()
                        .toBodilessEntity();
            case PUSH ->
                pushClient
                        .post()
                        .uri("/v1/messages:send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new PushRequest(target, templateId, payload))
                        .retrieve()
                        .toBodilessEntity();
        }
    }

    private static void requireConfigured(List<String> keyValuePairs) {
        StringBuilder missing = new StringBuilder();
        for (int i = 0; i < keyValuePairs.size(); i += 2) {
            if (keyValuePairs.get(i + 1).isBlank()) {
                missing.append(missing.isEmpty() ? "" : ", ").append(keyValuePairs.get(i));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("generic.notification.mode=vendor에는 다음 설정이 필요하다: " + missing);
        }
    }

    private static RestClient buildRestClient(String baseUrl, String apiKey, long timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> headers.setBearerAuth(apiKey))
                .build();
    }

    record EmailRequest(String sender, String to, String templateId, String payload) {}

    record SmsRequest(String senderNumber, String to, String templateId, String payload) {}

    record PushRequest(String token, String templateId, String payload) {}
}
