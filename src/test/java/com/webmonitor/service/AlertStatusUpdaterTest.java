package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.SiteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AlertStatusUpdaterTest {

    @Autowired
    private AlertStatusUpdater alertStatusUpdater;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private SiteRepository siteRepository;

    private Alert testAlert;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        siteRepository.deleteAll();

        Site testSite = siteRepository.save(Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .build());

        testAlert = alertRepository.save(Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("테스트 알림")
                .pageTitle("테스트 페이지")
                .detectedUrl("https://test.com/page")
                .site(testSite)
                .sent(false)
                .build());
    }

    @AfterEach
    void tearDown() {
        alertRepository.deleteAll();
        siteRepository.deleteAll();
    }

    @Test
    @DisplayName("발송 성공 시 sent=true, sentAt 설정, lastErrorMessage null")
    void updateAlertStatus_success_marksSentAndSetsTimestamp() {
        alertStatusUpdater.updateAlertStatus(testAlert.getId(), true, null);

        Alert updated = alertRepository.findById(testAlert.getId()).orElseThrow();
        assertThat(updated.getSent()).isTrue();
        assertThat(updated.getSentAt()).isNotNull();
        assertThat(updated.getLastErrorMessage()).isNull();
    }

    @Test
    @DisplayName("발송 실패 시 retryCount 증가, lastErrorMessage 설정")
    void updateAlertStatus_failure_incrementsRetryCountAndSetsErrorMessage() {
        alertStatusUpdater.updateAlertStatus(testAlert.getId(), false, "connection timeout");

        Alert updated = alertRepository.findById(testAlert.getId()).orElseThrow();
        assertThat(updated.getSent()).isFalse();
        assertThat(updated.getRetryCount()).isEqualTo(1);
        assertThat(updated.getLastErrorMessage()).isEqualTo("connection timeout");
    }

    @Test
    @DisplayName("발송 성공 시 retryCount 변경 없음")
    void updateAlertStatus_success_doesNotChangeRetryCount() {
        alertStatusUpdater.updateAlertStatus(testAlert.getId(), true, null);

        Alert updated = alertRepository.findById(testAlert.getId()).orElseThrow();
        assertThat(updated.getRetryCount()).isEqualTo(0);
    }
}
