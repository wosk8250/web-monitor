package com.webmonitor.scheduler;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlertQueueProcessor 스케줄러 테스트
 * 알림 큐 배치 처리 로직 검증
 */
@ExtendWith(MockitoExtension.class)
class AlertQueueProcessorTest {

    @Mock
    private AlertService alertService;

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertQueueProcessor alertQueueProcessor;

    @BeforeEach
    void setUp() {
        // @Value 필드 초기화 (기본값: 10)
        ReflectionTestUtils.setField(alertQueueProcessor, "batchSize", 10);
    }

    @Test
    @DisplayName("알림 큐 처리 정상 실행 - 미전송 알림 배치 처리")
    void processAlertQueue_정상실행_미전송알림배치처리() {
        // Given: 미전송 알림 5개 존재
        Site site = Site.builder()
                .id(1L)
                .name("Test Site")
                .url("https://test.com")
                .build();

        List<Alert> unsentAlerts = Arrays.asList(
                Alert.builder().id(1L).site(site).message("Alert 1").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build(),
                Alert.builder().id(2L).site(site).message("Alert 2").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build(),
                Alert.builder().id(3L).site(site).message("Alert 3").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build(),
                Alert.builder().id(4L).site(site).message("Alert 4").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build(),
                Alert.builder().id(5L).site(site).message("Alert 5").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build()
        );

        when(alertRepository.findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(unsentAlerts));

        // When: 알림 큐 처리 실행
        alertQueueProcessor.processAlertQueue();

        // Then: AlertService.sendAlertWithRetry()가 5번 호출되어야 함
        verify(alertService, times(5)).sendAlertWithRetry(anyLong());
    }

    @Test
    @DisplayName("알림 큐 처리 - 미전송 알림이 없는 경우 처리 안 함")
    void processAlertQueue_미전송알림없음_처리안함() {
        // Given: 미전송 알림이 없음
        when(alertRepository.findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(Collections.emptyList()));

        // When: 알림 큐 처리 실행
        alertQueueProcessor.processAlertQueue();

        // Then: AlertService.sendAlertWithRetry()가 호출되지 않아야 함
        verify(alertService, never()).sendAlertWithRetry(anyLong());
    }

    @Test
    @DisplayName("알림 큐 처리 - batchSize 제한 확인")
    void processAlertQueue_batchSize제한_배치크기만큼만처리() {
        // Given: batchSize = 3으로 설정
        ReflectionTestUtils.setField(alertQueueProcessor, "batchSize", 3);

        Site site = Site.builder()
                .id(1L)
                .name("Test Site")
                .url("https://test.com")
                .build();

        List<Alert> unsentAlerts = Arrays.asList(
                Alert.builder().id(1L).site(site).message("Alert 1").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build(),
                Alert.builder().id(2L).site(site).message("Alert 2").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build(),
                Alert.builder().id(3L).site(site).message("Alert 3").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build()
        );

        when(alertRepository.findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(unsentAlerts));

        // When: 알림 큐 처리 실행
        alertQueueProcessor.processAlertQueue();

        // Then: batchSize만큼만 처리되어야 함 (3개)
        verify(alertRepository).findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                eq(PageRequest.of(0, 3))
        );
        verify(alertService, times(3)).sendAlertWithRetry(anyLong());
    }

    @Test
    @DisplayName("알림 큐 처리 - 개별 알림 전송 실패 시 나머지 알림은 계속 처리")
    void processAlertQueue_개별알림전송실패_나머지알림계속처리() {
        // Given: 5개 알림 중 3번째 알림 전송 실패
        Site site = Site.builder()
                .id(1L)
                .name("Test Site")
                .url("https://test.com")
                .build();

        Alert alert1 = Alert.builder().id(1L).site(site).message("Alert 1").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build();
        Alert alert2 = Alert.builder().id(2L).site(site).message("Alert 2").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build();
        Alert alert3 = Alert.builder().id(3L).site(site).message("Alert 3").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build();
        Alert alert4 = Alert.builder().id(4L).site(site).message("Alert 4").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build();
        Alert alert5 = Alert.builder().id(5L).site(site).message("Alert 5").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build();

        List<Alert> unsentAlerts = Arrays.asList(alert1, alert2, alert3, alert4, alert5);

        when(alertRepository.findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(unsentAlerts));

        // 3번째 알림에서 예외 발생
        doNothing().when(alertService).sendAlertWithRetry(1L);
        doNothing().when(alertService).sendAlertWithRetry(2L);
        doThrow(new RuntimeException("전송 실패")).when(alertService).sendAlertWithRetry(3L);
        doNothing().when(alertService).sendAlertWithRetry(4L);
        doNothing().when(alertService).sendAlertWithRetry(5L);

        // When: 알림 큐 처리 실행
        alertQueueProcessor.processAlertQueue();

        // Then: 5번 모두 sendAlertWithRetry() 호출되어야 함 (실패해도 계속 진행)
        verify(alertService, times(5)).sendAlertWithRetry(anyLong());
    }

    @Test
    @DisplayName("알림 큐 처리 - Repository 조회 실패 시 로그 기록 후 종료")
    void processAlertQueue_Repository조회실패_로그기록후종료() {
        // Given: AlertRepository에서 예외 발생
        when(alertRepository.findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                any(Pageable.class)
        )).thenThrow(new RuntimeException("DB 조회 실패"));

        // When: 알림 큐 처리 실행
        alertQueueProcessor.processAlertQueue();

        // Then: AlertService.sendAlertWithRetry()가 호출되지 않아야 함
        verify(alertService, never()).sendAlertWithRetry(anyLong());
    }

    @Test
    @DisplayName("알림 큐 처리 - NORMAL 우선순위 알림만 처리 (retryCount < MAX_RETRIES)")
    void processAlertQueue_NORMAL우선순위알림만처리() {
        // Given: NORMAL 우선순위 알림 존재
        Site site = Site.builder()
                .id(1L)
                .name("Test Site")
                .url("https://test.com")
                .build();

        List<Alert> normalAlerts = Arrays.asList(
                Alert.builder().id(1L).site(site).message("Normal Alert").detectedUrl("https://test.com").detectedAt(LocalDateTime.now()).sent(false).priority(Alert.Priority.NORMAL).retryCount(0).build()
        );

        when(alertRepository.findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(normalAlerts));

        // When: 알림 큐 처리 실행
        alertQueueProcessor.processAlertQueue();

        // Then: NORMAL 우선순위 + 재시도 가능한 알림만 조회되어야 함
        verify(alertRepository).findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                eq(false),
                eq(Alert.Priority.NORMAL),
                eq(Alert.MAX_RETRIES),
                any(Pageable.class)
        );
        verify(alertService, times(1)).sendAlertWithRetry(anyLong());
    }
}
