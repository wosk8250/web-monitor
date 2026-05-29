package com.webmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 대시보드 통계 데이터 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatisticsResponse {

    /**
     * 전체 사이트 수
     */
    private long totalSites;

    /**
     * 활성화된 사이트 수
     */
    private long activeSites;

    /**
     * 전체 알림 수
     */
    private long totalAlerts;

    /**
     * 오늘 감지된 알림 수
     */
    private long todayAlerts;

    /**
     * 최근 알림 5개
     */
    private List<AlertResponse> recentAlerts;
}
