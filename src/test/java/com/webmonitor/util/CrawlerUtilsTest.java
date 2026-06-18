package com.webmonitor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CrawlerUtils 단위 테스트")
class CrawlerUtilsTest {

    private final CrawlerUtils crawlerUtils = new CrawlerUtils();

    @Test
    @DisplayName("validateUrl - file:// 프로토콜: IllegalArgumentException")
    void validateUrl_fileProtocol_throwsException() {
        assertThatThrownBy(() -> crawlerUtils.validateUrl("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateUrl - localhost: IllegalArgumentException")
    void validateUrl_localhost_throwsException() {
        assertThatThrownBy(() -> crawlerUtils.validateUrl("http://localhost/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateUrl - 비표준 포트: IllegalArgumentException")
    void validateUrl_nonStandardPort_throwsException() {
        assertThatThrownBy(() -> crawlerUtils.validateUrl("http://example.com:8080/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateUrl - 유효하지 않은 URL 형식: IllegalArgumentException")
    void validateUrl_malformedUrl_throwsException() {
        assertThatThrownBy(() -> crawlerUtils.validateUrl("not-a-url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
