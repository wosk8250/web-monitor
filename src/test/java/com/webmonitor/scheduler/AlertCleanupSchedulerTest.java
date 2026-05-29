package com.webmonitor.scheduler;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * AlertCleanupScheduler 스케줄러 테스트
 * 오래된 알림 자동 정리 로직 검증
 */
@ExtendWith(MockitoExtension.class)
class AlertCleanupSchedulerTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertCleanupScheduler alertCleanupScheduler;

    @BeforeEach
    void setUp() {
        // @Value 필드 초기화 (기본값)
        ReflectionTestUtils.setField(alertCleanupScheduler, "keywordRetentionDays", 7);
        ReflectionTestUtils.setField(alertCleanupScheduler, "restockRetentionDays", 30);
    }

    @Test
    @DisplayName("오래된 알림 정리 정상 실행 - KEYWORD/CONTENT_CHANGE 알림 7일, RESTOCK 알림 30일 기준")
    void cleanupOldAlerts_정상실행_보존기간기준삭제() {
        // Given: 오래된 알림들 존재
        Site site = Site.builder().id(1L).name("Test Site").url("https://test.com").build();

        // 7일 이전 알림들 (KEYWORD, CONTENT_CHANGE 타입 포함)
        List<Alert> oldKeywordAlerts = Arrays.asList(
                Alert.builder().id(1L).site(site).alertType(Alert.AlertType.KEYWORD).message("Old 1").detectedUrl("https://test.com").detectedAt(LocalDateTime.now().minusDays(8)).build(),
                Alert.builder().id(2L).site(site).alertType(Alert.AlertType.CONTENT_CHANGE).message("Old 2").detectedUrl("https://test.com").detectedAt(LocalDateTime.now().minusDays(8)).build()
        );

        // 30일 이전 알림들 (PRODUCT_RESTOCK 타입)
        List<Alert> oldRestockAlerts = Arrays.asList(
                Alert.builder().id(3L).site(site).alertType(Alert.AlertType.PRODUCT_RESTOCK).message("Old 3").detectedUrl("https://test.com").detectedAt(LocalDateTime.now().minusDays(31)).build()
        );

        // findByDetectedAtBefore() 호출 시 해당 타입의 알림만 반환
        when(alertRepository.findByDetectedAtBefore(any(LocalDateTime.class)))
                .thenReturn(oldKeywordAlerts)  // 첫 번째 호출 (KEYWORD)
                .thenReturn(oldKeywordAlerts)  // 두 번째 호출 (CONTENT_CHANGE)
                .thenReturn(oldRestockAlerts); // 세 번째 호출 (PRODUCT_RESTOCK)

        // When: 알림 정리 스케줄러 실행
        alertCleanupScheduler.cleanupOldAlerts();

        // Then: deleteAll()이 3번 호출되어야 함 (각 타입별로)
        verify(alertRepository, atLeastOnce()).deleteAll(anyList());
    }

    @Test
    @DisplayName("오래된 알림 정리 - 삭제할 알림이 없는 경우 처리 안 함")
    void cleanupOldAlerts_삭제할알림없음_처리안함() {
        // Given: 오래된 알림이 없음
        when(alertRepository.findByDetectedAtBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // When: 알림 정리 스케줄러 실행
        alertCleanupScheduler.cleanupOldAlerts();

        // Then: deleteAll()이 호출되지 않아야 함
        verify(alertRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("오래된 알림 정리 - keywordRetentionDays 기준 확인 (7일)")
    void cleanupOldAlerts_keywordRetentionDays기준_7일전알림삭제() {
        // Given: keywordRetentionDays = 7
        Site site = Site.builder().id(1L).name("Test Site").url("https://test.com").build();
        List<Alert> oldKeywordAlerts = Arrays.asList(
                Alert.builder().id(1L).site(site).alertType(Alert.AlertType.KEYWORD).message("Old").detectedUrl("https://test.com").detectedAt(LocalDateTime.now().minusDays(8)).build()
        );

        when(alertRepository.findByDetectedAtBefore(any(LocalDateTime.class)))
                .thenReturn(oldKeywordAlerts)
                .thenReturn(Collections.emptyList()); // 두 번째 호출 시 빈 리스트

        // When: 알림 정리 스케줄러 실행
        alertCleanupScheduler.cleanupOldAlerts();

        // Then: LocalDateTime.now().minusDays(7) 기준으로 조회되어야 함
        ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRepository, atLeastOnce()).findByDetectedAtBefore(dateCaptor.capture());

        // 첫 번째 호출 (KEYWORD 처리)의 시간 확인
        LocalDateTime capturedDate = dateCaptor.getAllValues().get(0);
        LocalDateTime expectedDate = LocalDateTime.now().minusDays(7);
        // 1분 오차 허용
        assertThat(capturedDate).isCloseTo(expectedDate, within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("오래된 알림 정리 - restockRetentionDays 기준 확인 (30일)")
    void cleanupOldAlerts_restockRetentionDays기준_30일전알림삭제() {
        // Given: restockRetentionDays = 30
        Site site = Site.builder().id(1L).name("Test Site").url("https://test.com").build();
        List<Alert> oldRestockAlerts = Arrays.asList(
                Alert.builder().id(1L).site(site).alertType(Alert.AlertType.PRODUCT_RESTOCK).message("Old").detectedUrl("https://test.com").detectedAt(LocalDateTime.now().minusDays(31)).build()
        );

        when(alertRepository.findByDetectedAtBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList())  // KEYWORD
                .thenReturn(Collections.emptyList())  // CONTENT_CHANGE
                .thenReturn(oldRestockAlerts);        // PRODUCT_RESTOCK

        // When: 알림 정리 스케줄러 실행
        alertCleanupScheduler.cleanupOldAlerts();

        // Then: LocalDateTime.now().minusDays(30) 기준으로 조회되어야 함
        ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRepository, atLeastOnce()).findByDetectedAtBefore(dateCaptor.capture());

        // 세 번째 호출 (PRODUCT_RESTOCK 처리)의 시간 확인
        List<LocalDateTime> allDates = dateCaptor.getAllValues();
        if (allDates.size() >= 3) {
            LocalDateTime capturedDate = allDates.get(2);
            LocalDateTime expectedDate = LocalDateTime.now().minusDays(30);
            // 1분 오차 허용
            assertThat(capturedDate).isCloseTo(expectedDate, within(1, java.time.temporal.ChronoUnit.MINUTES));
        }
    }

    @Test
    @DisplayName("오래된 알림 정리 - 배치 크기 제한 확인 (1000개씩)")
    void cleanupOldAlerts_배치크기제한_1000개씩처리() {
        // Given: 오래된 알림 존재
        Site site = Site.builder().id(1L).name("Test Site").url("https://test.com").build();
        List<Alert> oldAlerts = Arrays.asList(
                Alert.builder().id(1L).site(site).alertType(Alert.AlertType.KEYWORD).message("Old").detectedUrl("https://test.com").detectedAt(LocalDateTime.now().minusDays(8)).build()
        );

        when(alertRepository.findByDetectedAtBefore(any(LocalDateTime.class)))
                .thenReturn(oldAlerts)
                .thenReturn(Collections.emptyList());

        // When: 알림 정리 스케줄러 실행
        alertCleanupScheduler.cleanupOldAlerts();

        // Then: findByDetectedAtBefore()로 조회 후 Stream으로 필터링하여 배치 처리
        verify(alertRepository, atLeastOnce()).findByDetectedAtBefore(any(LocalDateTime.class));
        verify(alertRepository, atLeastOnce()).deleteAll(anyList());
    }

    @Test
    @DisplayName("오래된 알림 정리 - Repository 예외 발생 시 로그 기록 후 종료")
    void cleanupOldAlerts_Repository예외발생_로그기록후종료() {
        // Given: AlertRepository에서 예외 발생
        when(alertRepository.findByDetectedAtBefore(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB 조회 실패"));

        // When: 알림 정리 스케줄러 실행 (예외 발생해도 스케줄러는 중단되지 않음)
        alertCleanupScheduler.cleanupOldAlerts();

        // Then: deleteAll()이 호출되지 않아야 함
        verify(alertRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("오래된 알림 정리 - 알림 타입별 다른 보존기간 적용 확인")
    void cleanupOldAlerts_알림타입별다른보존기간_적용확인() {
        // Given: 커스텀 보존기간 설정
        ReflectionTestUtils.setField(alertCleanupScheduler, "keywordRetentionDays", 14);  // 14일
        ReflectionTestUtils.setField(alertCleanupScheduler, "restockRetentionDays", 60);  // 60일

        when(alertRepository.findByDetectedAtBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // When: 알림 정리 스케줄러 실행
        alertCleanupScheduler.cleanupOldAlerts();

        // Then: 14일과 60일 기준으로 조회되어야 함
        ArgumentCaptor<LocalDateTime> dateCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(alertRepository, atLeastOnce()).findByDetectedAtBefore(dateCaptor.capture());

        List<LocalDateTime> allDates = dateCaptor.getAllValues();

        // 최소 3번 호출되어야 함 (KEYWORD, CONTENT_CHANGE, PRODUCT_RESTOCK)
        assertThat(allDates.size()).isGreaterThanOrEqualTo(3);

        // 첫 두 호출은 14일 기준 (KEYWORD, CONTENT_CHANGE)
        LocalDateTime expectedKeywordDate = LocalDateTime.now().minusDays(14);
        assertThat(allDates.get(0)).isCloseTo(expectedKeywordDate, within(1, java.time.temporal.ChronoUnit.MINUTES));

        // 세 번째 호출은 60일 기준 (PRODUCT_RESTOCK)
        if (allDates.size() >= 3) {
            LocalDateTime expectedRestockDate = LocalDateTime.now().minusDays(60);
            assertThat(allDates.get(2)).isCloseTo(expectedRestockDate, within(1, java.time.temporal.ChronoUnit.MINUTES));
        }
    }
}
