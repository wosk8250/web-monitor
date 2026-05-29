package com.webmonitor.util;

import lombok.experimental.UtilityClass;

/**
 * Discord 관련 유틸리티 클래스
 * 웹훅 URL 검증 및 마스킹 등의 공통 기능 제공
 */
@UtilityClass
public class DiscordUtils {

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

        String url = webhookUrl.trim().toLowerCase();
        return url.startsWith("https://discord.com/api/webhooks/") ||
               url.startsWith("https://discordapp.com/api/webhooks/");
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
