package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.StatisticsResponse;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsService 단위 테스트")
class StatisticsServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    @DisplayName("통계 조회 - 정상 케이스: 모든 필드 올바르게 집계")
    void getStatistics_happyPath_returnsCorrectFields() {
        when(siteRepository.count()).thenReturn(10L);
        when(siteRepository.countByActive(true)).thenReturn(7L);
        when(alertRepository.count()).thenReturn(50L);
        when(alertRepository.countByDetectedAtAfter(any(LocalDateTime.class))).thenReturn(3L);
        when(alertRepository.findTop5ByOrderByDetectedAtDesc()).thenReturn(List.of());

        StatisticsResponse result = statisticsService.getStatistics();

        assertThat(result.getTotalSites()).isEqualTo(10L);
        assertThat(result.getActiveSites()).isEqualTo(7L);
        assertThat(result.getTotalAlerts()).isEqualTo(50L);
        assertThat(result.getTodayAlerts()).isEqualTo(3L);
        assertThat(result.getRecentAlerts()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("통계 조회 - 빈 데이터베이스: 모든 필드 0이고 목록은 빈 리스트")
    void getStatistics_emptyDatabase_returnsAllZeros() {
        when(siteRepository.count()).thenReturn(0L);
        when(siteRepository.countByActive(true)).thenReturn(0L);
        when(alertRepository.count()).thenReturn(0L);
        when(alertRepository.countByDetectedAtAfter(any(LocalDateTime.class))).thenReturn(0L);
        when(alertRepository.findTop5ByOrderByDetectedAtDesc()).thenReturn(List.of());

        StatisticsResponse result = statisticsService.getStatistics();

        assertThat(result.getTotalSites()).isZero();
        assertThat(result.getActiveSites()).isZero();
        assertThat(result.getTotalAlerts()).isZero();
        assertThat(result.getTodayAlerts()).isZero();
        assertThat(result.getRecentAlerts()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("통계 조회 - 최근 알림 5개 반환")
    void getStatistics_withAlerts_recentAlertsHasFiveEntries() {
        Site site = Site.builder().name("테스트").url("https://test.com").active(true).build();

        List<Alert> alerts = List.of(
            buildAlert(site, "알림1"),
            buildAlert(site, "알림2"),
            buildAlert(site, "알림3"),
            buildAlert(site, "알림4"),
            buildAlert(site, "알림5")
        );

        when(siteRepository.count()).thenReturn(1L);
        when(siteRepository.countByActive(true)).thenReturn(1L);
        when(alertRepository.count()).thenReturn(5L);
        when(alertRepository.countByDetectedAtAfter(any(LocalDateTime.class))).thenReturn(5L);
        when(alertRepository.findTop5ByOrderByDetectedAtDesc()).thenReturn(alerts);

        StatisticsResponse result = statisticsService.getStatistics();

        assertThat(result.getRecentAlerts()).hasSize(5);
    }

    @Test
    @DisplayName("통계 조회 - 레포지토리 예외 시 빈 통계 반환 (예외 전파 없음)")
    void getStatistics_repositoryThrows_returnsEmptyStatistics() {
        when(siteRepository.count()).thenThrow(new RuntimeException("DB 연결 실패"));

        StatisticsResponse result = statisticsService.getStatistics();

        assertThat(result.getTotalSites()).isZero();
        assertThat(result.getActiveSites()).isZero();
        assertThat(result.getTotalAlerts()).isZero();
        assertThat(result.getTodayAlerts()).isZero();
        assertThat(result.getRecentAlerts()).isNotNull().isEmpty();
    }

    private Alert buildAlert(Site site, String message) {
        return Alert.builder()
                .site(site)
                .alertType(Alert.AlertType.KEYWORD)
                .message(message)
                .detectedUrl("https://test.com")
                .detectedAt(LocalDateTime.now())
                .sent(false)
                .priority(Alert.Priority.NORMAL)
                .build();
    }
}
