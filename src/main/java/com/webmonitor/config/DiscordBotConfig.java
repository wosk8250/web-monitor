package com.webmonitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 디스코드 봇 설정 정보
 */
@Configuration
@ConfigurationProperties(prefix = "discord.bot")
@Data
public class DiscordBotConfig {

    /**
     * 디스코드 봇 토큰
     */
    private String token;

    /**
     * 디스코드 봇 활성화 여부
     */
    private boolean enabled = false;
}
