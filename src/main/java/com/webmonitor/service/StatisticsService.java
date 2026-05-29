package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.dto.AlertResponse;
import com.webmonitor.dto.StatisticsResponse;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 대시보드 통계 데이터를 제공하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsService {

    private final SiteRepository siteRepository;
    private final AlertRepository alertRepository;

    /**
     * 대시보드 통계 데이터 조회
     * @return 통계 응답 DTO
     */
    public StatisticsResponse getStatistics() {
        log.info("대시보드 통계 데이터 조회 시작");

        try {
            // 전체 사이트 수
            long totalSites = siteRepository.count();
            log.debug("전체 사이트 수: {}", totalSites);

            // 활성화된 사이트 수
            long activeSites = siteRepository.countByActive(true);
            log.debug("활성화된 사이트 수: {}", activeSites);

            // 전체 알림 수
            long totalAlerts = alertRepository.count();
            log.debug("전체 알림 수: {}", totalAlerts);

            // 오늘 감지된 알림 수
            LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            long todayAlerts = alertRepository.countByDetectedAtAfter(todayStart);
            log.debug("오늘 알림 수: {}", todayAlerts);

            // 최근 알림 5개
            List<Alert> recentAlertsEntities = alertRepository.findTop5ByOrderByDetectedAtDesc();
            List<AlertResponse> recentAlerts = recentAlertsEntities.stream()
                    .map(AlertResponse::from)
                    .collect(Collectors.toList());
            log.debug("최근 알림 수: {}", recentAlerts.size());

            log.info("대시보드 통계 데이터 조회 완료");

            return StatisticsResponse.builder()
                    .totalSites(totalSites)
                    .activeSites(activeSites)
                    .totalAlerts(totalAlerts)
                    .todayAlerts(todayAlerts)
                    .recentAlerts(recentAlerts)
                    .build();

        } catch (Exception e) {
            log.error("통계 데이터 조회 중 오류 발생: {}", e.getMessage(), e);
            // 빈 통계 반환 (프론트엔드에서 처리 가능하도록)
            return StatisticsResponse.builder()
                    .totalSites(0L)
                    .activeSites(0L)
                    .totalAlerts(0L)
                    .todayAlerts(0L)
                    .recentAlerts(List.of())
                    .build();
        }
    }
}
