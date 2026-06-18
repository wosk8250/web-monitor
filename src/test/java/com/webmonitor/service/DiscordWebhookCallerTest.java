package com.webmonitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordWebhookCaller 단위 테스트")
class DiscordWebhookCallerTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private DiscordWebhookCaller caller;

    @BeforeEach
    void setUp() {
        // 재시도 대기를 0ms로 설정해 테스트 속도 확보
        ReflectionTestUtils.setField(caller, "retryInitialDelayMs", 0L);
        ReflectionTestUtils.setField(caller, "rateLimitWaitMs", 0L);
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 2xx 성공: RestTemplate 1회 호출 후 ResponseEntity 반환")
    void sendDiscordWebhookWithRetry_success_returnsResponse() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("OK"));

        ResponseEntity<String> result = caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트");

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 5xx 오류 후 성공: RestTemplate 2회 호출")
    void sendDiscordWebhookWithRetry_serverErrorThenSuccess_retries() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE))
                .thenReturn(ResponseEntity.ok("OK"));

        assertThatCode(() -> caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트"))
                .doesNotThrowAnyException();

        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 모든 재시도 실패: MAX_RETRIES(3)회 후 RuntimeException")
    void sendDiscordWebhookWithRetry_allRetriesFail_throwsAfterMaxRetries() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트"))
                .isInstanceOf(RuntimeException.class);

        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 401 인증 오류: 즉시 RuntimeException, RestTemplate 1회만")
    void sendDiscordWebhookWithRetry_401_failsImmediately() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("인증/권한 오류");

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 403 권한 오류: 즉시 RuntimeException, RestTemplate 1회만")
    void sendDiscordWebhookWithRetry_403_failsImmediately() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트"))
                .isInstanceOf(RuntimeException.class);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 404 Not Found: 즉시 RuntimeException, RestTemplate 1회만")
    void sendDiscordWebhookWithRetry_404_failsImmediately() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("웹훅 URL 없음");

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 429 Rate Limit: 대기 후 재시도 성공, RestTemplate 2회 호출")
    void sendDiscordWebhookWithRetry_429_retriesAfterWait() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))
                .thenReturn(ResponseEntity.ok("OK"));

        assertThatCode(() -> caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트"))
                .doesNotThrowAnyException();

        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendDiscordWebhookWithRetry - 5xx 응답코드(예외 없음): 3회 재시도 후 RuntimeException")
    void sendDiscordWebhookWithRetry_non2xxResponse_throwsAfterMaxRetries() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());

        assertThatThrownBy(() -> caller.sendDiscordWebhookWithRetry(
                "https://discord.com/api/webhooks/1/token", buildRequest(), "테스트"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("최대 재시도 횟수 초과");

        verify(restTemplate, times(3)).postForEntity(anyString(), any(), eq(String.class));
    }

    private HttpEntity<Map<String, Object>> buildRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("content", "test");
        return new HttpEntity<>(body);
    }
}
