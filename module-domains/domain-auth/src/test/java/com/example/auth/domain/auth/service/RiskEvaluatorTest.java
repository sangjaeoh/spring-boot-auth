package com.example.auth.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.common.messaging.MessagePublisher;
import com.example.auth.domain.auth.entity.LoginAttempt;
import com.example.auth.domain.auth.entity.LoginResult;
import com.example.auth.domain.auth.event.NewLocationDetected;
import com.example.auth.domain.auth.info.RiskAssessmentInfo;
import com.example.auth.domain.auth.port.GeoIpLookup;
import com.example.auth.domain.auth.port.GeoLocation;
import com.example.auth.domain.auth.repository.LoginAttemptRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RiskEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private LoginAttemptRepository repository;
    private GeoIpLookup geoIpLookup;
    private MessagePublisher messagePublisher;
    private RiskEvaluator evaluator;

    @BeforeEach
    void setUp() {
        repository = mock(LoginAttemptRepository.class);
        geoIpLookup = mock(GeoIpLookup.class);
        messagePublisher = mock(MessagePublisher.class);
        evaluator = new RiskEvaluator(repository, geoIpLookup, messagePublisher, 80);
        when(geoIpLookup.lookup(any())).thenReturn(Optional.of(new GeoLocation("KR")));
        when(repository.findTop20ByUserIdAndResultOrderByAtDesc(USER_ID, LoginResult.SUCCESS))
                .thenReturn(List.of());
        when(repository.countByUserIdAndResultAndAtAfter(eq(USER_ID), eq(LoginResult.FAILURE), any()))
                .thenReturn(0L);
    }

    @Test
    void knownDeviceAndLocationWithoutFailuresScoresZero() {
        when(repository.findTop20ByUserIdAndResultOrderByAtDesc(USER_ID, LoginResult.SUCCESS))
                .thenReturn(List.of(successAttempt("KR")));

        RiskAssessmentInfo risk = evaluator.evaluate(USER_ID, false, "127.0.0.1", NOW);

        assertThat(risk.riskScore()).isZero();
        assertThat(risk.countryCode()).isEqualTo("KR");
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void newDeviceRaisesScore() {
        RiskAssessmentInfo risk = evaluator.evaluate(USER_ID, true, "127.0.0.1", NOW);

        assertThat(risk.riskScore()).isEqualTo(40);
    }

    @Test
    void newCountryRaisesScoreAndPublishesNewLocationDetected() {
        when(geoIpLookup.lookup("198.51.100.7")).thenReturn(Optional.of(new GeoLocation("US")));
        when(repository.findTop20ByUserIdAndResultOrderByAtDesc(USER_ID, LoginResult.SUCCESS))
                .thenReturn(List.of(successAttempt("KR")));

        RiskAssessmentInfo risk = evaluator.evaluate(USER_ID, false, "198.51.100.7", NOW);

        assertThat(risk.riskScore()).isEqualTo(40);
        assertThat(risk.countryCode()).isEqualTo("US");
        ArgumentCaptor<NewLocationDetected> captor = ArgumentCaptor.forClass(NewLocationDetected.class);
        verify(messagePublisher).publish(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().countryCode()).isEqualTo("US");
    }

    @Test
    void firstLoginWithoutSuccessHistoryIsNotNewLocation() {
        RiskAssessmentInfo risk = evaluator.evaluate(USER_ID, false, "127.0.0.1", NOW);

        assertThat(risk.riskScore()).isZero();
        verify(messagePublisher, never()).publish(any());
    }

    @Test
    void recentFailuresAddCappedScore() {
        when(repository.countByUserIdAndResultAndAtAfter(eq(USER_ID), eq(LoginResult.FAILURE), any()))
                .thenReturn(9L);
        when(repository.findTop20ByUserIdAndResultOrderByAtDesc(USER_ID, LoginResult.SUCCESS))
                .thenReturn(List.of(successAttempt("KR")));

        RiskAssessmentInfo risk = evaluator.evaluate(USER_ID, false, "127.0.0.1", NOW);

        assertThat(risk.riskScore()).isEqualTo(20);
    }

    @Test
    void unknownLocationSkipsLocationSignal() {
        when(geoIpLookup.lookup(any())).thenReturn(Optional.empty());

        RiskAssessmentInfo risk = evaluator.evaluate(USER_ID, false, "10.0.0.1", NOW);

        assertThat(risk.riskScore()).isZero();
        assertThat(risk.countryCode()).isNull();
        verify(messagePublisher, never()).publish(any());
    }

    private LoginAttempt successAttempt(String countryCode) {
        return LoginAttempt.create(USER_ID, LoginResult.SUCCESS, null, "127.0.0.1", null, 0, countryCode, NOW);
    }
}
