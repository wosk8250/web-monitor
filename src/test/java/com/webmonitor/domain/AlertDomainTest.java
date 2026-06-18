package com.webmonitor.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Alert 도메인 단위 테스트")
class AlertDomainTest {

    @Test
    @DisplayName("syncPriority - PRODUCT_RESTOCK: priority=HIGH")
    void syncPriority_productRestock_setsHigh() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.PRODUCT_RESTOCK)
                .message("재입고")
                .detectedUrl("https://shop.com/product")
                .build();

        alert.syncPriority();

        assertThat(alert.getPriority()).isEqualTo(Alert.Priority.HIGH);
    }

    @Test
    @DisplayName("syncPriority - KEYWORD: priority=NORMAL")
    void syncPriority_keyword_setsNormal() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("키워드")
                .detectedUrl("https://test.com")
                .build();

        alert.syncPriority();

        assertThat(alert.getPriority()).isEqualTo(Alert.Priority.NORMAL);
    }

    @Test
    @DisplayName("syncPriority - CONTENT_CHANGE: priority=NORMAL")
    void syncPriority_contentChange_setsNormal() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.CONTENT_CHANGE)
                .message("변경")
                .detectedUrl("https://test.com")
                .build();

        alert.syncPriority();

        assertThat(alert.getPriority()).isEqualTo(Alert.Priority.NORMAL);
    }

    @Test
    @DisplayName("syncPriority - alertType 변경 후 재호출: priority 갱신됨")
    void syncPriority_afterAlertTypeChange_priorityUpdated() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.PRODUCT_RESTOCK)
                .message("재입고")
                .detectedUrl("https://shop.com/product")
                .build();
        alert.syncPriority();
        assertThat(alert.getPriority()).isEqualTo(Alert.Priority.HIGH);

        alert.setAlertType(Alert.AlertType.KEYWORD);
        alert.syncPriority();

        assertThat(alert.getPriority()).isEqualTo(Alert.Priority.NORMAL);
    }

    @Test
    @DisplayName("canRetry - retryCount < MAX_RETRIES: true")
    void canRetry_belowMax_returnsTrue() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("알림")
                .detectedUrl("https://test.com")
                .retryCount(2)
                .build();

        assertThat(alert.canRetry()).isTrue();
    }

    @Test
    @DisplayName("canRetry - retryCount == MAX_RETRIES: false")
    void canRetry_atMax_returnsFalse() {
        Alert alert = Alert.builder()
                .alertType(Alert.AlertType.KEYWORD)
                .message("알림")
                .detectedUrl("https://test.com")
                .retryCount(Alert.MAX_RETRIES)
                .build();

        assertThat(alert.canRetry()).isFalse();
    }
}
