package com.webmonitor.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SettingRequest {

    @Size(max = 500)
    private String discordWebhookUrl;

    private Boolean enabled;
}
