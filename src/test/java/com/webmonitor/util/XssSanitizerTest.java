package com.webmonitor.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XssSanitizer 유틸리티 테스트
 * HTML 이스케이프를 통한 XSS 방어 검증
 */
class XssSanitizerTest {

    @Test
    void sanitize_withScriptTag_shouldEscapeHtml() {
        // Given
        String maliciousInput = "<script>alert('XSS')</script>";

        // When
        String sanitized = XssSanitizer.sanitize(maliciousInput);

        // Then - HTML 태그가 이스케이프되어야 함
        assertThat(sanitized).isEqualTo("&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;");
        assertThat(sanitized).doesNotContain("<script>");
        assertThat(sanitized).doesNotContain("</script>");
    }

    @Test
    void sanitize_withImgOnError_shouldEscapeHtml() {
        // Given
        String maliciousInput = "<img src=x onerror=alert('XSS')>";

        // When
        String sanitized = XssSanitizer.sanitize(maliciousInput);

        // Then - HTML 태그가 이스케이프되어야 함
        assertThat(sanitized).isEqualTo("&lt;img src=x onerror=alert(&#39;XSS&#39;)&gt;");
        assertThat(sanitized).doesNotContain("<img");
        assertThat(sanitized).doesNotContain(">");
        assertThat(sanitized).contains("&lt;");
        assertThat(sanitized).contains("&gt;");
    }

    @Test
    void sanitize_withIframeTag_shouldEscapeHtml() {
        // Given
        String maliciousInput = "<iframe src=\"javascript:alert('XSS')\"></iframe>";

        // When
        String sanitized = XssSanitizer.sanitize(maliciousInput);

        // Then
        assertThat(sanitized).doesNotContain("<iframe");
        assertThat(sanitized).doesNotContain("</iframe>");
        assertThat(sanitized).contains("&lt;iframe");
        assertThat(sanitized).contains("&lt;/iframe&gt;");
    }

    @Test
    void sanitize_withOnClickEvent_shouldEscapeHtml() {
        // Given
        String maliciousInput = "<a href=\"#\" onclick=\"alert('XSS')\">Click me</a>";

        // When
        String sanitized = XssSanitizer.sanitize(maliciousInput);

        // Then - HTML 태그와 따옴표가 이스케이프되어야 함
        assertThat(sanitized).doesNotContain("<a ");
        assertThat(sanitized).doesNotContain("</a>");
        assertThat(sanitized).contains("&lt;a");
        assertThat(sanitized).contains("&gt;");
        assertThat(sanitized).contains("&quot;");  // 따옴표도 이스케이프됨
    }

    @Test
    void sanitize_withStyleTag_shouldEscapeHtml() {
        // Given
        String maliciousInput = "<style>body { background: red; }</style>";

        // When
        String sanitized = XssSanitizer.sanitize(maliciousInput);

        // Then
        assertThat(sanitized).doesNotContain("<style>");
        assertThat(sanitized).contains("&lt;style&gt;");
    }

    @Test
    void sanitize_withNull_shouldReturnNull() {
        // Given
        String nullInput = null;

        // When
        String sanitized = XssSanitizer.sanitize(nullInput);

        // Then
        assertThat(sanitized).isNull();
    }

    @Test
    void sanitize_withEmptyString_shouldReturnEmpty() {
        // Given
        String emptyInput = "";

        // When
        String sanitized = XssSanitizer.sanitize(emptyInput);

        // Then
        assertThat(sanitized).isEmpty();
    }

    @Test
    void sanitize_withLegitimateText_shouldNotModify() {
        // Given
        String legitimateInput = "Hello World! This is a normal text.";

        // When
        String sanitized = XssSanitizer.sanitize(legitimateInput);

        // Then - 일반 텍스트는 변경되지 않아야 함
        assertThat(sanitized).isEqualTo(legitimateInput);
    }

    @Test
    void sanitize_withSpecialCharactersOnly_shouldEscape() {
        // Given
        String specialChars = "< > & \" '";

        // When
        String sanitized = XssSanitizer.sanitize(specialChars);

        // Then
        assertThat(sanitized).isEqualTo("&lt; &gt; &amp; &quot; &#39;");
    }

    @Test
    void sanitize_withMixedContent_shouldEscapeOnlyHtml() {
        // Given
        String mixedInput = "Normal text <script>alert(1)</script> more normal text";

        // When
        String sanitized = XssSanitizer.sanitize(mixedInput);

        // Then
        assertThat(sanitized).contains("Normal text");
        assertThat(sanitized).contains("more normal text");
        assertThat(sanitized).doesNotContain("<script>");
        assertThat(sanitized).contains("&lt;script&gt;");
    }

    @Test
    void sanitize_withDoubleQuotes_shouldEscape() {
        // Given
        String input = "Test \"quoted\" text";

        // When
        String sanitized = XssSanitizer.sanitize(input);

        // Then
        assertThat(sanitized).isEqualTo("Test &quot;quoted&quot; text");
    }

    @Test
    void sanitize_withAmpersand_shouldEscape() {
        // Given
        String input = "Tom & Jerry";

        // When
        String sanitized = XssSanitizer.sanitize(input);

        // Then
        assertThat(sanitized).isEqualTo("Tom &amp; Jerry");
    }

    @Test
    void sanitize_withKoreanText_shouldPreserve() {
        // Given
        String koreanInput = "안녕하세요! 한글 테스트입니다.";

        // When
        String sanitized = XssSanitizer.sanitize(koreanInput);

        // Then - 한글은 그대로 유지되어야 함
        assertThat(sanitized).isEqualTo(koreanInput);
    }

    @Test
    void sanitize_withKoreanAndXss_shouldEscapeXssOnly() {
        // Given
        String mixedInput = "안녕하세요 <script>alert('XSS')</script> 한글입니다";

        // When
        String sanitized = XssSanitizer.sanitize(mixedInput);

        // Then
        assertThat(sanitized).contains("안녕하세요");
        assertThat(sanitized).contains("한글입니다");
        assertThat(sanitized).doesNotContain("<script>");
        assertThat(sanitized).contains("&lt;script&gt;");
    }

    @Test
    void sanitize_withUrl_shouldEscapeHtmlTagsOnly() {
        // Given
        String urlInput = "https://example.com?param=value&other=<script>";

        // When
        String sanitized = XssSanitizer.sanitize(urlInput);

        // Then - URL은 유지되지만 script 태그는 이스케이프
        assertThat(sanitized).contains("https://example.com?param=value");
        assertThat(sanitized).doesNotContain("<script>");
        assertThat(sanitized).contains("&lt;script&gt;");
    }
}
