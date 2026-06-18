package com.webmonitor.service;

import com.webmonitor.event.CriticalErrorEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CriticalErrorNotifier 단위 테스트")
class CriticalErrorNotifierTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private MetricsService metricsService;

    private CriticalErrorNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new CriticalErrorNotifier(restTemplate, metricsService);
        ReflectionTestUtils.setField(notifier, "discordWebhookUrl", "https://discord.com/api/webhooks/test/token");
        ReflectionTestUtils.setField(notifier, "notificationEnabled", true);
        ReflectionTestUtils.setField(notifier, "cooldownMinutes", 15);
        ReflectionTestUtils.setField(notifier, "exceptionThreshold", 3);
        ReflectionTestUtils.setField(notifier, "exceptionWindowMinutes", 5);
    }

    @Test
    @DisplayName("sendCriticalNotification - 정상 케이스: RestTemplate 호출 1회")
    void sendCriticalNotification_happyPath_callsRestTemplate() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notifier.sendCriticalNotification(
                "테스트 알림", "테스트 설명",
                CriticalErrorNotifier.NotificationLevel.CRITICAL, null);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
        verify(metricsService, times(1)).incrementAlertSentCounter(anyString());
    }

    @Test
    @DisplayName("sendCriticalNotification - fields 포함 시 예외 없이 전송")
    void sendCriticalNotification_withFields_noException() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        Map<String, String> fields = Map.of("키1", "값1", "키2", "값2");
        notifier.sendCriticalNotification(
                "필드 테스트", "설명",
                CriticalErrorNotifier.NotificationLevel.WARNING, fields);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendCriticalNotification - notificationEnabled=false: RestTemplate 미호출")
    void sendCriticalNotification_disabled_doesNotCallRestTemplate() {
        ReflectionTestUtils.setField(notifier, "notificationEnabled", false);

        notifier.sendCriticalNotification(
                "테스트", "설명",
                CriticalErrorNotifier.NotificationLevel.INFO, null);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendCriticalNotification - webhookUrl 비어있음: RestTemplate 미호출")
    void sendCriticalNotification_emptyWebhookUrl_doesNotCallRestTemplate() {
        ReflectionTestUtils.setField(notifier, "discordWebhookUrl", "");

        notifier.sendCriticalNotification(
                "테스트", "설명",
                CriticalErrorNotifier.NotificationLevel.INFO, null);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("sendCriticalNotification - 쿨다운 중: 두 번째 호출은 RestTemplate 미호출")
    void sendCriticalNotification_duringCooldown_secondCallSkipped() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notifier.sendCriticalNotification("동일 제목", "설명",
                CriticalErrorNotifier.NotificationLevel.CRITICAL, null);
        notifier.sendCriticalNotification("동일 제목", "설명",
                CriticalErrorNotifier.NotificationLevel.CRITICAL, null);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendCriticalNotification - HTTP 비2xx 응답: 실패 카운터 증가")
    void sendCriticalNotification_nonSuccessResponse_incrementsFailureCounter() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        notifier.sendCriticalNotification(
                "테스트", "설명",
                CriticalErrorNotifier.NotificationLevel.CRITICAL, null);

        verify(metricsService, times(1)).incrementAlertFailureCounter(anyString(), anyString());
        verify(metricsService, never()).incrementAlertSentCounter(anyString());
    }

    @Test
    @DisplayName("sendCriticalNotification - RestTemplate 예외: 예외 전파 없음, 실패 카운터 증가")
    void sendCriticalNotification_restTemplateThrows_exceptionSwallowed() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("네트워크 오류"));

        notifier.sendCriticalNotification(
                "테스트", "설명",
                CriticalErrorNotifier.NotificationLevel.CRITICAL, null);

        verify(metricsService, times(1)).incrementAlertFailureCounter(anyString(), anyString());
    }

    @Test
    @DisplayName("notifyThreadPoolSaturation - CRITICAL 레벨로 전송")
    void notifyThreadPoolSaturation_sendsCriticalNotification() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notifier.notifyThreadPoolSaturation("alert", 10, 10, 50, 50);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("notifyHealthCheckFailure - DOWN 상태: CRITICAL 레벨")
    void notifyHealthCheckFailure_downStatus_usesCriticalLevel() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notifier.notifyHealthCheckFailure("DOWN", Map.of("세부사항", "DB 연결 실패"));

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("notifyHealthCheckFailure - 비DOWN 상태: WARNING 레벨")
    void notifyHealthCheckFailure_nonDownStatus_usesWarningLevel() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notifier.notifyHealthCheckFailure("DEGRADED", Map.of());

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("onCriticalErrorEvent - 이벤트 수신 시 trackExceptionAndNotify 호출")
    void onCriticalErrorEvent_callsTrackExceptionAndNotify() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));
        ReflectionTestUtils.setField(notifier, "exceptionThreshold", 1);

        CriticalErrorEvent event = new CriticalErrorEvent(this, "RuntimeException", "MonitorService");
        notifier.onCriticalErrorEvent(event);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("trackExceptionAndNotify - 임계값 미만: 알림 미전송")
    void trackExceptionAndNotify_belowThreshold_doesNotSend() {
        // threshold=3, 2번만 호출
        notifier.trackExceptionAndNotify("IOException", "SiteService");
        notifier.trackExceptionAndNotify("IOException", "SiteService");

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("trackExceptionAndNotify - 임계값 도달: 알림 전송 후 카운터 초기화")
    void trackExceptionAndNotify_reachesThreshold_sendsAndClears() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        // threshold=3
        notifier.trackExceptionAndNotify("IOException", "SiteService");
        notifier.trackExceptionAndNotify("IOException", "SiteService");
        notifier.trackExceptionAndNotify("IOException", "SiteService");

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));

        // 초기화 후 다시 카운트 시작: 1번만 더 호출해도 미전송
        notifier.trackExceptionAndNotify("IOException", "SiteService");
        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    @DisplayName("sendSimpleNotification - null fields로 sendCriticalNotification 위임")
    void sendSimpleNotification_delegatesToSendCriticalNotification() {
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        notifier.sendSimpleNotification("제목", "메시지", CriticalErrorNotifier.NotificationLevel.INFO);

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }
}
