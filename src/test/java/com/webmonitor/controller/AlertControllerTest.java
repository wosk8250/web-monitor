package com.webmonitor.controller;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.AlertPatchRequest;
import com.webmonitor.dto.AlertResponse;
import com.webmonitor.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private com.webmonitor.repository.SiteRepository siteRepository;

    private Site testSite;
    private Alert testAlert1;
    private Alert testAlert2;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        siteRepository.deleteAll();

        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .detectContentChange(false)
                .build();
        testSite = siteRepository.save(testSite);

        testAlert1 = Alert.builder()
                .site(testSite)
                .message("[테스트 사이트] 키워드 '테스트' 감지 - 테스트 제목")
                .pageTitle("테스트 제목")
                .detectedUrl("https://test.com/page1")
                .sent(false)
                .build();

        testAlert2 = Alert.builder()
                .site(testSite)
                .message("[테스트 사이트] 새 글 - 다른 제목")
                .pageTitle("다른 제목")
                .detectedUrl("https://test.com/page2")
                .sent(true)
                .build();
    }

    @Test
    @DisplayName("DELETE /api/alerts - 모든 알림 삭제 (정상 케이스)")
    void deleteAllAlerts_Success() {
        alertRepository.save(testAlert1);
        alertRepository.save(testAlert2);

        long initialCount = alertRepository.count();
        assertThat(initialCount).isEqualTo(2);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts",
                HttpMethod.DELETE,
                null,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("2건의 알림이 삭제되었습니다");

        long finalCount = alertRepository.count();
        assertThat(finalCount).isZero();
    }

    @Test
    @DisplayName("DELETE /api/alerts - 알림이 없을 때")
    void deleteAllAlerts_NoAlerts() {
        assertThat(alertRepository.count()).isZero();

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts",
                HttpMethod.DELETE,
                null,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("0건의 알림이 삭제되었습니다");
    }

    @Test
    @DisplayName("DELETE /api/alerts?sent=true - 읽은 알림만 삭제")
    void deleteSentAlerts_Success() {
        alertRepository.save(testAlert1); // sent=false
        alertRepository.save(testAlert2); // sent=true

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts?sent=true",
                HttpMethod.DELETE,
                null,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("1건의 알림이 삭제되었습니다");
        assertThat(alertRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("GET /api/alerts - 모든 알림 조회 (정상 케이스)")
    void getAllAlerts_Success() {
        alertRepository.save(testAlert1);
        alertRepository.save(testAlert2);

        ResponseEntity<AlertResponse[]> response = restTemplate.getForEntity(
                "/api/alerts",
                AlertResponse[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("GET /api/alerts?sent=false - 읽지 않은 알림만 조회")
    void getUnsentAlerts_Success() {
        alertRepository.save(testAlert1); // sent=false
        alertRepository.save(testAlert2); // sent=true

        ResponseEntity<AlertResponse[]> response = restTemplate.getForEntity(
                "/api/alerts?sent=false",
                AlertResponse[].class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getSent()).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/alerts/{id} - 단일 알림 삭제 (정상 케이스)")
    void deleteAlert_Success() {
        Alert savedAlert = alertRepository.save(testAlert1);

        restTemplate.delete("/api/alerts/" + savedAlert.getId());

        assertThat(alertRepository.existsById(savedAlert.getId())).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/alerts/{id} - 존재하지 않는 ID: 404 반환")
    void deleteAlert_NotFound() {
        Long nonExistentId = 99999L;

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/alerts/" + nonExistentId,
                HttpMethod.DELETE,
                null,
                Void.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /api/alerts/{id} - sent=true body로 읽음 처리 (정상 케이스)")
    void patchAlertSent_Success() {
        Alert savedAlert = alertRepository.save(testAlert1);
        assertThat(savedAlert.getSent()).isFalse();

        HttpEntity<AlertPatchRequest> body = new HttpEntity<>(new AlertPatchRequest(true));
        ResponseEntity<AlertResponse> response = restTemplate.exchange(
                "/api/alerts/" + savedAlert.getId(),
                HttpMethod.PATCH,
                body,
                AlertResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSent()).isTrue();
        assertThat(response.getBody().getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("PATCH /api/alerts/{id} - sent=false body: 400 반환")
    void patchAlertSent_unsupportedValue_returns400() {
        Alert savedAlert = alertRepository.save(testAlert1);

        HttpEntity<AlertPatchRequest> body = new HttpEntity<>(new AlertPatchRequest(false));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts/" + savedAlert.getId(),
                HttpMethod.PATCH,
                body,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("DELETE /api/alerts?sent=false - 지원하지 않는 값: 400 반환")
    void deleteAlerts_sentFalse_returns400() {
        alertRepository.save(testAlert1);
        alertRepository.save(testAlert2);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts?sent=false",
                HttpMethod.DELETE,
                null,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(alertRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("GET /api/alerts?from=... - to 없이 from만 전달: 400 반환")
    void getAlerts_onlyFrom_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/alerts?from=2024-01-01T00:00:00", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/alerts?to=... - from 없이 to만 전달: 400 반환")
    void getAlerts_onlyTo_returns400() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/alerts?to=2024-12-31T23:59:59", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("PATCH /api/alerts/{id} - sent=null body: 400 반환")
    void patchAlertSent_nullValue_returns400() {
        Alert savedAlert = alertRepository.save(testAlert1);

        HttpEntity<AlertPatchRequest> body = new HttpEntity<>(new AlertPatchRequest(null));
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts/" + savedAlert.getId(),
                HttpMethod.PATCH,
                body,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
