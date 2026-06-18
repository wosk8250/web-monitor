package com.webmonitor.controller;

import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("SseController 통합 테스트")
class SseControllerTest {

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
    @DisplayName("GET /api/sse/connections - 연결 수 조회: 200 반환")
    void getConnections_returns200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/sse/connections", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("GET /api/sse/connections - 응답에 connectedClients 필드 포함")
    void getConnections_responseContainsConnectedClientsField() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/sse/connections", String.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("connectedClients");
    }

    @Test
    @DisplayName("DELETE /api/sse/connections - 모든 연결 종료: 200 반환")
    void closeAllConnections_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/sse/connections", HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
}
