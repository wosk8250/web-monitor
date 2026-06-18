package com.webmonitor.controller;

import com.webmonitor.dto.StatisticsResponse;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("StatisticsController 통합 테스트")
class StatisticsControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SiteRepository siteRepository;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        siteRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/stats - 정상 케이스: HTTP 200 반환")
    void getStatistics_returns200() {
        ResponseEntity<StatisticsResponse> response = restTemplate.getForEntity(
                "/api/stats", StatisticsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /api/stats - 응답 바디에 필드 포함")
    void getStatistics_responseBodyNotNull() {
        ResponseEntity<StatisticsResponse> response = restTemplate.getForEntity(
                "/api/stats", StatisticsResponse.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getRecentAlerts()).isNotNull();
    }

    @Test
    @DisplayName("GET /api/stats - 빈 DB: 모든 카운트 0, 목록 비어있음")
    void getStatistics_emptyDatabase_returnsZeros() {
        ResponseEntity<StatisticsResponse> response = restTemplate.getForEntity(
                "/api/stats", StatisticsResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        StatisticsResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTotalSites()).isZero();
        assertThat(body.getActiveSites()).isZero();
        assertThat(body.getTotalAlerts()).isZero();
        assertThat(body.getTodayAlerts()).isZero();
        assertThat(body.getRecentAlerts()).isEmpty();
    }
}
