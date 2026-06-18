package com.webmonitor.dto;

import com.webmonitor.domain.Setting;

import java.time.LocalDateTime;

public record SettingResponse(
        Long id,
        String discordWebhookUrl,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SettingResponse from(Setting setting) {
        return new SettingResponse(
                setting.getId(),
                setting.getDiscordWebhookUrl(),
                setting.getEnabled(),
                setting.getCreatedAt(),
                setting.getUpdatedAt()
        );
    }
}
