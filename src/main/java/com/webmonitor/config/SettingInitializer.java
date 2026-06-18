package com.webmonitor.config;

import com.webmonitor.dto.SettingRequest;
import com.webmonitor.service.SettingService;
import com.webmonitor.util.DiscordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettingInitializer implements ApplicationRunner {

    private final SettingService settingService;

    @Value("${discord.webhook.url:}")
    private String discordWebhookUrl;

    @Override
    public void run(ApplicationArguments args) {
        discordWebhookUrl = discordWebhookUrl.trim();
        try {
            if (settingService.hasAnySetting()) {
                log.debug("Setting 이미 존재 — 자동 초기화 건너뜀");
                return;
            }
            // isBlank → INFO, format mismatch → WARN 로그 레벨을 구분하기 위해 별도 가드 유지
            if (discordWebhookUrl.isBlank()) {
                log.info("DISCORD_WEBHOOK_URL 미설정 — Setting 초기화 건너뜀");
                return;
            }
            if (!DiscordUtils.isValidDiscordWebhookUrl(discordWebhookUrl)) {
                log.warn("DISCORD_WEBHOOK_URL 형식 불일치 — Setting 초기화 건너뜀: {}",
                        DiscordUtils.maskWebhookUrl(discordWebhookUrl));
                return;
            }
            settingService.createSetting(new SettingRequest(discordWebhookUrl, true));
            log.info("Setting 자동 초기화 완료 (DISCORD_WEBHOOK_URL)");
        } catch (DataIntegrityViolationException e) {
            // 다중 인스턴스 동시 기동 시 다른 인스턴스가 먼저 INSERT — 정상 상황
            log.info("Setting 자동 초기화 — 다른 인스턴스에서 이미 초기화 완료 (정상)");
        } catch (Exception e) {
            log.warn("Setting 자동 초기화 실패 — 앱은 계속 기동됨", e);
        }
    }
}
