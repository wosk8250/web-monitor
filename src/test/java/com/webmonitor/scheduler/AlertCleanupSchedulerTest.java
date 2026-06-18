package com.webmonitor.scheduler;

import com.webmonitor.domain.Alert;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlertCleanupScheduler 테스트")
class AlertCleanupSchedulerTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertCleanupScheduler alertCleanupScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertCleanupScheduler, "keywordRetentionDays", 7);
        ReflectionTestUtils.setField(alertCleanupScheduler, "restockRetentionDays", 30);
        ReflectionTestUtils.setField(alertCleanupScheduler, "maxAlertsPerSite", 50);
        // 기본: 삭제 0건 반환
        when(alertRepository.deleteByAlertTypeAndDetectedAtBefore(any(), any())).thenReturn(0);
    }

    @Test
    @DisplayName("cleanupOldAlerts - 각 타입별로 deleteByAlertTypeAndDetectedAtBefore 3회 호출")
    void cleanupOldAlerts_callsDeleteForEachType() {
        alertCleanupScheduler.cleanupOldAlerts();

        verify(alertRepository, times(1)).deleteByAlertTypeAndDetectedAtBefore(
                eq(Alert.AlertType.KEYWORD), any(LocalDateTime.class));
        verify(alertRepository, times(1)).deleteByAlertTypeAndDetectedAtBefore(
                eq(Alert.AlertType.CONTENT_CHANGE), any(LocalDateTime.class));
        verify(alertRepository, times(1)).deleteByAlertTypeAndDetectedAtBefore(
                eq(Alert.AlertType.PRODUCT_RESTOCK), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("cleanupOldAlerts - KEYWORD/CONTENT_CHANGE cutoff는 keywordRetentionDays(7일) 기준")
    void cleanupOldAlerts_keywordCutoffIsSevenDaysAgo() {
        alertCleanupScheduler.cleanupOldAlerts();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRepository, atLeastOnce())
                .deleteByAlertTypeAndDetectedAtBefore(eq(Alert.AlertType.KEYWORD), captor.capture());

        LocalDateTime expected = LocalDateTime.now().minusDays(7);
        assertThat(captor.getValue()).isCloseTo(expected, within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("cleanupOldAlerts - PRODUCT_RESTOCK cutoff는 restockRetentionDays(30일) 기준")
    void cleanupOldAlerts_restockCutoffIsThirtyDaysAgo() {
        alertCleanupScheduler.cleanupOldAlerts();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRepository, atLeastOnce())
                .deleteByAlertTypeAndDetectedAtBefore(eq(Alert.AlertType.PRODUCT_RESTOCK), captor.capture());

        LocalDateTime expected = LocalDateTime.now().minusDays(30);
        assertThat(captor.getValue()).isCloseTo(expected, within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("cleanupOldAlerts - 커스텀 보존기간(14일/60일) 적용")
    void cleanupOldAlerts_customRetentionDays_usedAsExpected() {
        ReflectionTestUtils.setField(alertCleanupScheduler, "keywordRetentionDays", 14);
        ReflectionTestUtils.setField(alertCleanupScheduler, "restockRetentionDays", 60);

        alertCleanupScheduler.cleanupOldAlerts();

        ArgumentCaptor<LocalDateTime> kwCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRepository).deleteByAlertTypeAndDetectedAtBefore(eq(Alert.AlertType.KEYWORD), kwCaptor.capture());
        assertThat(kwCaptor.getValue()).isCloseTo(
                LocalDateTime.now().minusDays(14), within(1, ChronoUnit.MINUTES));

        ArgumentCaptor<LocalDateTime> rsCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRepository).deleteByAlertTypeAndDetectedAtBefore(eq(Alert.AlertType.PRODUCT_RESTOCK), rsCaptor.capture());
        assertThat(rsCaptor.getValue()).isCloseTo(
                LocalDateTime.now().minusDays(60), within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("cleanupOldAlerts - 삭제 건수 로깅 (삭제 발생 시)")
    void cleanupOldAlerts_withDeletions_logsCount() {
        when(alertRepository.deleteByAlertTypeAndDetectedAtBefore(
                eq(Alert.AlertType.KEYWORD), any())).thenReturn(5);

        // 예외 없이 완료 + 3번 호출됨
        alertCleanupScheduler.cleanupOldAlerts();

        verify(alertRepository, times(3))
                .deleteByAlertTypeAndDetectedAtBefore(any(Alert.AlertType.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("cleanupOldAlerts - Repository 예외 발생 시 스케줄러 중단 안 됨")
    void cleanupOldAlerts_repositoryThrows_doesNotPropagate() {
        when(alertRepository.deleteByAlertTypeAndDetectedAtBefore(any(), any()))
                .thenThrow(new RuntimeException("DB 오류"));

        // 예외 전파 없이 완료
        alertCleanupScheduler.cleanupOldAlerts();
    }

    @Test
    @DisplayName("cleanupExcessAlerts - alertService.cleanupAllExcessAlerts(maxAlertsPerSite) 호출")
    void cleanupExcessAlerts_delegatesToAlertService() {
        alertCleanupScheduler.cleanupExcessAlerts();

        verify(alertService, times(1)).cleanupAllExcessAlerts(50);
    }
}
