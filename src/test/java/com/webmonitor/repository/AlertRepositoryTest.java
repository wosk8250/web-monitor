package com.webmonitor.repository;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertRepository 통합 테스트
 * H2 in-memory 데이터베이스를 사용한 Repository 계층 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
class AlertRepositoryTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private KeywordRepository keywordRepository;

    private Site testSite;
    private Keyword testKeyword;

    @BeforeEach
    void setUp() {
        // 테스트 사이트 생성
        testSite = Site.builder()
                .name("Test Site")
                .url("https://example.com")
                .active(true)
                .checkIntervalMinutes(5)
                .build();
        siteRepository.save(testSite);

        // 테스트 키워드 생성
        testKeyword = Keyword.builder()
                .keyword("test")
                .site(testSite)
                .active(true)
                .build();
        keywordRepository.save(testKeyword);
    }

    @Test
    void save_shouldPersistAlert() {
        // Given
        Alert alert = createTestAlert("Test Alert", "https://example.com/article1");

        // When
        Alert savedAlert = alertRepository.save(alert);

        // Then
        assertThat(savedAlert.getId()).isNotNull();
        assertThat(savedAlert.getMessage()).isEqualTo("Test Alert");
        assertThat(savedAlert.getSent()).isFalse();
        assertThat(savedAlert.getPriority()).isEqualTo(Alert.Priority.NORMAL);
    }

    @Test
    void findBySite_shouldReturnAlertsForSite() {
        // Given
        Alert alert1 = createTestAlert("Alert 1", "https://example.com/1");
        Alert alert2 = createTestAlert("Alert 2", "https://example.com/2");
        alertRepository.saveAll(List.of(alert1, alert2));

        // When
        List<Alert> results = alertRepository.findBySite(testSite);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Alert::getMessage)
                .containsExactlyInAnyOrder("Alert 1", "Alert 2");
    }

    @Test
    void findByKeyword_shouldReturnAlertsForKeyword() {
        // Given
        Alert alert = createTestAlert("Keyword Alert", "https://example.com/keyword");
        alertRepository.save(alert);

        // When
        List<Alert> results = alertRepository.findByKeyword(testKeyword);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMessage()).isEqualTo("Keyword Alert");
    }

    @Test
    void findBySent_shouldReturnUnsentAlerts() {
        // Given
        Alert unsentAlert = createTestAlert("Unsent", "https://example.com/unsent");
        Alert sentAlert = createTestAlert("Sent", "https://example.com/sent");
        sentAlert.setSent(true);
        sentAlert.setSentAt(LocalDateTime.now());
        alertRepository.saveAll(List.of(unsentAlert, sentAlert));

        // When
        List<Alert> unsentResults = alertRepository.findBySent(false);
        List<Alert> sentResults = alertRepository.findBySent(true);

        // Then
        assertThat(unsentResults).hasSize(1);
        assertThat(unsentResults.get(0).getMessage()).isEqualTo("Unsent");
        assertThat(sentResults).hasSize(1);
        assertThat(sentResults.get(0).getMessage()).isEqualTo("Sent");
    }

    @Test
    void findByDetectedAtBetween_shouldReturnAlertsInPeriod() {
        // Given
        Alert oldAlert = createTestAlert("Old Alert", "https://example.com/old");
        Alert recentAlert = createTestAlert("Recent Alert", "https://example.com/recent");
        alertRepository.saveAll(List.of(oldAlert, recentAlert));
        alertRepository.flush();

        LocalDateTime start = LocalDateTime.now().minusMinutes(1);
        LocalDateTime end = LocalDateTime.now().plusMinutes(1);

        // When
        List<Alert> results = alertRepository.findByDetectedAtBetween(start, end);

        // Then
        assertThat(results).hasSize(2);
    }

    @Test
    void findBySiteAndSent_shouldReturnUnsentAlertsForSite() {
        // Given
        Alert unsent = createTestAlert("Unsent for site", "https://example.com/unsent");
        alertRepository.save(unsent);

        // When
        List<Alert> results = alertRepository.findBySiteAndSent(testSite, false);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSent()).isFalse();
    }

    @Test
    void findAllByOrderByDetectedAtDesc_shouldReturnLatestFirst() throws Exception {
        // Given
        Alert alert1 = createTestAlert("First", "https://example.com/1");
        Thread.sleep(10); // 시간 차이를 위해
        Alert alert2 = createTestAlert("Second", "https://example.com/2");
        alertRepository.saveAll(List.of(alert1, alert2));

        // When
        List<Alert> results = alertRepository.findAllByOrderByDetectedAtDesc();

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getMessage()).isEqualTo("Second");
        assertThat(results.get(1).getMessage()).isEqualTo("First");
    }

    @Test
    void findBySentOrderByDetectedAtDesc_shouldReturnSortedBySentStatus() {
        // Given
        Alert unsent = createTestAlert("Unsent", "https://example.com/unsent");
        Alert sent = createTestAlert("Sent", "https://example.com/sent");
        sent.setSent(true);
        alertRepository.saveAll(List.of(unsent, sent));

        // When
        List<Alert> sentResults = alertRepository.findBySentOrderByDetectedAtDesc(true);

        // Then
        assertThat(sentResults).hasSize(1);
        assertThat(sentResults.get(0).getSent()).isTrue();
    }

    @Test
    void countBySite_shouldReturnCorrectCount() {
        // Given
        Alert alert1 = createTestAlert("Alert 1", "https://example.com/1");
        Alert alert2 = createTestAlert("Alert 2", "https://example.com/2");
        alertRepository.saveAll(List.of(alert1, alert2));

        // When
        long count = alertRepository.countBySite(testSite);

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void findBySiteOrderByDetectedAtAsc_shouldReturnOldestFirst() throws Exception {
        // Given
        Alert alert1 = createTestAlert("Old", "https://example.com/old");
        Thread.sleep(10);
        Alert alert2 = createTestAlert("New", "https://example.com/new");
        alertRepository.saveAll(List.of(alert1, alert2));

        // When
        List<Alert> results = alertRepository.findBySiteOrderByDetectedAtAsc(testSite);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getMessage()).isEqualTo("Old");
        assertThat(results.get(1).getMessage()).isEqualTo("New");
    }

    @Test
    void countByDetectedAtAfter_shouldReturnRecentCount() {
        // Given
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        Alert recentAlert = createTestAlert("Recent", "https://example.com/recent");
        alertRepository.save(recentAlert);

        // When
        long count = alertRepository.countByDetectedAtAfter(threshold);

        // Then
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void deleteById_shouldRemoveAlert() {
        // Given
        Alert alert = createTestAlert("To be deleted", "https://example.com/delete");
        Alert saved = alertRepository.save(alert);

        // When
        alertRepository.deleteById(saved.getId());

        // Then
        assertThat(alertRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void deleteBySiteId_shouldRemoveAllAlertsForSite() {
        // Given
        Alert alert1 = createTestAlert("Alert 1", "https://example.com/1");
        Alert alert2 = createTestAlert("Alert 2", "https://example.com/2");
        alertRepository.saveAll(List.of(alert1, alert2));

        // When
        alertRepository.deleteBySiteId(testSite.getId());
        alertRepository.flush();

        // Then
        List<Alert> remainingAlerts = alertRepository.findBySite(testSite);
        assertThat(remainingAlerts).isEmpty();
    }

    @Test
    void findAllByOrderByDetectedAtDesc_withPageable_shouldReturnPagedResults() {
        // Given
        for (int i = 0; i < 15; i++) {
            Alert alert = createTestAlert("Alert " + i, "https://example.com/" + i);
            alertRepository.save(alert);
        }

        // When
        Page<Alert> page = alertRepository.findAllByOrderByDetectedAtDesc(PageRequest.of(0, 10));

        // Then
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void save_withPriority_shouldRespectAlertType() {
        // Given - KEYWORD 타입은 NORMAL 우선순위
        Alert keywordAlert = Alert.builder()
                .site(testSite)
                .keyword(testKeyword)
                .alertType(Alert.AlertType.KEYWORD)
                .message("Keyword alert")
                .detectedUrl("https://example.com/keyword")
                .build();

        // When
        Alert saved = alertRepository.save(keywordAlert);

        // Then
        assertThat(saved.getPriority()).isEqualTo(Alert.Priority.NORMAL);
    }

    @Test
    void save_withRetryCount_shouldTrackRetries() {
        // Given
        Alert alert = createTestAlert("Alert with retries", "https://example.com/retry");
        alert.incrementRetryCount();
        alert.incrementRetryCount();

        // When
        Alert saved = alertRepository.save(alert);

        // Then
        assertThat(saved.getRetryCount()).isEqualTo(2);
        assertThat(saved.canRetry()).isTrue();
    }

    /**
     * 테스트용 Alert 생성 헬퍼 메서드
     */
    private Alert createTestAlert(String message, String url) {
        return Alert.builder()
                .site(testSite)
                .keyword(testKeyword)
                .alertType(Alert.AlertType.KEYWORD)
                .message(message)
                .detectedUrl(url)
                .sent(false)
                .retryCount(0)
                .build();
    }
}
