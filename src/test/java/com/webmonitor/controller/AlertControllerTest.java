package com.webmonitor.controller;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.AlertResponse;
import com.webmonitor.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertController 통합 테스트
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
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
        // 테스트 데이터 초기화
        alertRepository.deleteAll();
        siteRepository.deleteAll();

        // 테스트용 사이트 생성 및 저장
        testSite = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .checkInterval(10)
                .active(true)
                .detectContentChange(false)
                .build();
        testSite = siteRepository.save(testSite);

        // 테스트용 알림 생성
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
                .sent(true) // 읽은 알림
                .build();
    }

    @Test
    @DisplayName("모든 알림 삭제 - 정상 케이스")
    void deleteAllAlerts_Success() {
        // Given
        alertRepository.save(testAlert1);
        alertRepository.save(testAlert2);

        long initialCount = alertRepository.count();
        assertThat(initialCount).isEqualTo(2);

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts/all",
                org.springframework.http.HttpMethod.DELETE,
                null,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("2건의 알림이 삭제되었습니다");

        long finalCount = alertRepository.count();
        assertThat(finalCount).isZero();
    }

    @Test
    @DisplayName("모든 알림 삭제 - 알림이 없을 때")
    void deleteAllAlerts_NoAlerts() {
        // Given
        assertThat(alertRepository.count()).isZero();

        // When
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/alerts/all",
                org.springframework.http.HttpMethod.DELETE,
                null,
                String.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("0건의 알림이 삭제되었습니다");
    }

    @Test
    @DisplayName("모든 알림 조회 - 정상 케이스")
    void getAllAlerts_Success() {
        // Given
        alertRepository.save(testAlert1);
        alertRepository.save(testAlert2);

        // When
        ResponseEntity<AlertResponse[]> response = restTemplate.getForEntity(
                "/api/alerts",
                AlertResponse[].class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    @Test
    @DisplayName("읽지 않은 알림 조회")
    void getUnsentAlerts_Success() {
        // Given
        alertRepository.save(testAlert1); // 읽지 않음
        alertRepository.save(testAlert2); // 읽음

        // When
        ResponseEntity<AlertResponse[]> response = restTemplate.getForEntity(
                "/api/alerts/unsent",
                AlertResponse[].class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getSent()).isFalse();
    }

    @Test
    @DisplayName("단일 알림 삭제 - 정상 케이스")
    void deleteAlert_Success() {
        // Given
        Alert savedAlert = alertRepository.save(testAlert1);

        // When
        restTemplate.delete("/api/alerts/" + savedAlert.getId());

        // Then
        assertThat(alertRepository.existsById(savedAlert.getId())).isFalse();
    }

    @Test
    @DisplayName("단일 알림 삭제 - 존재하지 않는 ID")
    void deleteAlert_NotFound() {
        // Given
        Long nonExistentId = 99999L;

        // When
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/alerts/" + nonExistentId,
                org.springframework.http.HttpMethod.DELETE,
                null,
                Void.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("알림 읽음 처리 - 정상 케이스")
    void markAlertAsSent_Success() {
        // Given
        Alert savedAlert = alertRepository.save(testAlert1);
        assertThat(savedAlert.getSent()).isFalse();

        // When
        ResponseEntity<AlertResponse> response = restTemplate.exchange(
                "/api/alerts/" + savedAlert.getId() + "/mark-sent",
                org.springframework.http.HttpMethod.PATCH,
                null,
                AlertResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSent()).isTrue();
        assertThat(response.getBody().getSentAt()).isNotNull();
    }
}
