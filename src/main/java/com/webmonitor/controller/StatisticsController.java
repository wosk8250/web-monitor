package com.webmonitor.controller;

import com.webmonitor.dto.StatisticsResponse;
import com.webmonitor.service.StatisticsService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 통계 REST API 컨트롤러
 * Rate Limiter 적용: API 과부하 방지 (10초당 최대 100 요청)
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Slf4j
@RateLimiter(name = "api")
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 대시보드 통계 조회
     * GET /api/stats
     * @return 통계 데이터
     */
    @GetMapping
    public ResponseEntity<StatisticsResponse> getStatistics() {
        log.info("GET /api/stats - 통계 조회 요청");
        StatisticsResponse statistics = statisticsService.getStatistics();
        return ResponseEntity.ok(statistics);
    }
}
