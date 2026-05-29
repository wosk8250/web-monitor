package com.webmonitor.dto;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Site;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AlertResponse DTO 테스트
 * NPE 방어 및 null 처리 테스트
 */
class AlertResponseTest {

    @Test
    void from_withAllFields_shouldConvertSuccessfully() {
        // Given
        Site site = Site.builder()
                .id(1L)
                .name("Test Site")
                .url("https://test.com")
                .build();

        Keyword keyword = Keyword.builder()
                .id(2L)
                .keyword("Test Keyword")
                .build();

        Alert alert = Alert.builder()
                .id(100L)
                .site(site)
                .keyword(keyword)
                .message("Test message")
                .pageTitle("Test Page")
                .detectedUrl("https://test.com/page")
                .detectedAt(LocalDateTime.now())
                .sent(false)
                .build();

        // When
        AlertResponse response = AlertResponse.from(alert);

        // Then
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getSiteId()).isEqualTo(1L);
        assertThat(response.getSiteName()).isEqualTo("Test Site");
        assertThat(response.getSiteUrl()).isEqualTo("https://test.com");
        assertThat(response.getKeywordId()).isEqualTo(2L);
        assertThat(response.getKeywordText()).isEqualTo("Test Keyword");
        assertThat(response.getMessage()).isEqualTo("Test message");
        assertThat(response.getPageTitle()).isEqualTo("Test Page");
        assertThat(response.getDetectedUrl()).isEqualTo("https://test.com/page");
        assertThat(response.getSent()).isFalse();
    }

    @Test
    void from_withNullSite_shouldHandleGracefully() {
        // Given - PRODUCT_RESTOCK 알림의 경우 site가 null일 수 있음
        Product product = Product.builder()
                .id(5L)
                .name("Test Product")
                .build();

        Alert alert = Alert.builder()
                .id(200L)
                .site(null)  // PRODUCT_RESTOCK 알림: site는 null
                .keyword(null)  // 키워드 없음
                .product(product)
                .alertType(Alert.AlertType.PRODUCT_RESTOCK)
                .message("제품 재입고 알림")
                .pageTitle("Product Page")
                .detectedUrl("https://shop.com/product/123")
                .detectedAt(LocalDateTime.now())
                .sent(false)
                .build();

        // When - NPE가 발생하지 않아야 함
        AlertResponse response = AlertResponse.from(alert);

        // Then - null 필드는 null로 반환되어야 함
        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getSiteId()).isNull();  // NPE 방어 성공
        assertThat(response.getSiteName()).isNull();  // NPE 방어 성공
        assertThat(response.getSiteUrl()).isNull();  // NPE 방어 성공
        assertThat(response.getKeywordId()).isNull();
        assertThat(response.getKeywordText()).isNull();
        assertThat(response.getMessage()).isEqualTo("제품 재입고 알림");
        assertThat(response.getDetectedUrl()).isEqualTo("https://shop.com/product/123");
    }

    @Test
    void from_withNullKeyword_shouldHandleGracefully() {
        // Given - CONTENT_CHANGE 알림의 경우 keyword가 null일 수 있음
        Site site = Site.builder()
                .id(3L)
                .name("News Site")
                .url("https://news.com")
                .build();

        Alert alert = Alert.builder()
                .id(300L)
                .site(site)
                .keyword(null)  // CONTENT_CHANGE 알림: keyword는 null
                .alertType(Alert.AlertType.CONTENT_CHANGE)
                .message("페이지 변경 감지")
                .pageTitle("News Page")
                .detectedUrl("https://news.com/article")
                .detectedAt(LocalDateTime.now())
                .sent(true)
                .sentAt(LocalDateTime.now())
                .build();

        // When
        AlertResponse response = AlertResponse.from(alert);

        // Then
        assertThat(response.getId()).isEqualTo(300L);
        assertThat(response.getSiteId()).isEqualTo(3L);
        assertThat(response.getSiteName()).isEqualTo("News Site");
        assertThat(response.getSiteUrl()).isEqualTo("https://news.com");
        assertThat(response.getKeywordId()).isNull();  // keyword가 null이므로
        assertThat(response.getKeywordText()).isNull();  // keyword가 null이므로
        assertThat(response.getMessage()).isEqualTo("페이지 변경 감지");
        assertThat(response.getSent()).isTrue();
    }

    @Test
    void from_withNullSiteAndKeyword_shouldHandleGracefully() {
        // Given - 극단적인 케이스: site와 keyword 모두 null
        Alert alert = Alert.builder()
                .id(400L)
                .site(null)
                .keyword(null)
                .message("Test message")
                .pageTitle("Test Page")
                .detectedUrl("https://test.com")
                .detectedAt(LocalDateTime.now())
                .sent(false)
                .build();

        // When
        AlertResponse response = AlertResponse.from(alert);

        // Then - NPE 없이 변환되어야 함
        assertThat(response.getId()).isEqualTo(400L);
        assertThat(response.getSiteId()).isNull();
        assertThat(response.getSiteName()).isNull();
        assertThat(response.getSiteUrl()).isNull();
        assertThat(response.getKeywordId()).isNull();
        assertThat(response.getKeywordText()).isNull();
        assertThat(response.getMessage()).isEqualTo("Test message");
        assertThat(response.getDetectedUrl()).isEqualTo("https://test.com");
    }

    @Test
    void from_withSentAlert_shouldIncludeSentAt() {
        // Given
        Site site = Site.builder()
                .id(1L)
                .name("Test Site")
                .url("https://test.com")
                .build();

        LocalDateTime sentTime = LocalDateTime.now();
        Alert alert = Alert.builder()
                .id(500L)
                .site(site)
                .message("Sent alert")
                .detectedUrl("https://test.com")
                .detectedAt(LocalDateTime.now())
                .sent(true)
                .sentAt(sentTime)
                .build();

        // When
        AlertResponse response = AlertResponse.from(alert);

        // Then
        assertThat(response.getSent()).isTrue();
        assertThat(response.getSentAt()).isEqualTo(sentTime);
    }
}
