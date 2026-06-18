package com.webmonitor.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordUtilsTest {

    @ParameterizedTest
    @DisplayName("유효한 Discord 웹훅 URL은 true를 반환한다")
    @ValueSource(strings = {
            "https://discord.com/api/webhooks/123456789/abcToken-xyz_123",
            "https://discordapp.com/api/webhooks/987654321/ABCDEF-token_999",
            "HTTPS://DISCORD.COM/api/webhooks/111/token",   // 대소문자 무관
    })
    void isValidDiscordWebhookUrl_ValidUrls_ReturnsTrue(String url) {
        assertThat(DiscordUtils.isValidDiscordWebhookUrl(url)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("유효하지 않은 URL은 false를 반환한다")
    @ValueSource(strings = {
            "https://discord.com/api/webhooks/",            // id/token 없음 (prefix-only)
            "https://discord.com/api/webhooks/123456789",   // token 없음
            "http://discord.com/api/webhooks/123/token",    // http (비 HTTPS)
            "https://evil.com/api/webhooks/123/token",      // 잘못된 도메인
            "https://discord.com/webhooks/123/token",       // /api 경로 누락
            "not-a-url",
            "",
            "   ",
    })
    void isValidDiscordWebhookUrl_InvalidUrls_ReturnsFalse(String url) {
        assertThat(DiscordUtils.isValidDiscordWebhookUrl(url)).isFalse();
    }

    @Test
    @DisplayName("null은 false를 반환한다")
    void isValidDiscordWebhookUrl_Null_ReturnsFalse() {
        assertThat(DiscordUtils.isValidDiscordWebhookUrl(null)).isFalse();
    }

    @Test
    @DisplayName("50자 미만 URL은 마스킹 결과가 ***이다")
    void maskWebhookUrl_ShortUrl_ReturnsMasked() {
        assertThat(DiscordUtils.maskWebhookUrl("https://discord.com")).isEqualTo("***");
    }

    @Test
    @DisplayName("50자 이상 URL은 앞 40자 + ***으로 마스킹된다")
    void maskWebhookUrl_LongUrl_ReturnsTruncated() {
        String url = "https://discord.com/api/webhooks/123456789/verylongtokenhere";
        String masked = DiscordUtils.maskWebhookUrl(url);
        assertThat(masked).endsWith("***");
        assertThat(masked).startsWith(url.substring(0, 40));
    }

    @Test
    @DisplayName("null URL 마스킹 시 ***을 반환한다")
    void maskWebhookUrl_Null_ReturnsMasked() {
        assertThat(DiscordUtils.maskWebhookUrl(null)).isEqualTo("***");
    }
}
