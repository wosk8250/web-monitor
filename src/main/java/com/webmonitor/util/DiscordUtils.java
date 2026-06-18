package com.webmonitor.util;

import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

/**
 * Discord 관련 유틸리티 클래스
 * 웹훅 URL 검증 및 마스킹 등의 공통 기능 제공
 */
@UtilityClass
public class DiscordUtils {

    // {id}/{token} 두 세그먼트 모두 필수 — prefix-only URL 차단
    private static final Pattern WEBHOOK_URL_PATTERN = Pattern.compile(
            "https://(discord\\.com|discordapp\\.com)/api/webhooks/\\d+/[\\w-]+",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 디스코드 웹훅 URL 형식 검증
     *
     * @param webhookUrl 검증할 URL
     * @return 유효한 형식이면 true
     */
    public static boolean isValidDiscordWebhookUrl(String webhookUrl) {
        if (webhookUrl == null) {
            return false;
        }
        return WEBHOOK_URL_PATTERN.matcher(webhookUrl.trim()).matches();
    }

    /**
     * 웹훅 URL 마스킹 (보안을 위해 일부만 표시)
     *
     * @param webhookUrl 마스킹할 URL
     * @return 마스킹된 URL
     */
    public static String maskWebhookUrl(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.length() < 50) {
            return "***";
        }
        return webhookUrl.substring(0, 40) + "***";
    }
}
