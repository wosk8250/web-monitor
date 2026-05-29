package com.webmonitor.repository;

import com.webmonitor.domain.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SettingRepository 통합 테스트
 * H2 in-memory 데이터베이스를 사용한 Repository 계층 테스트
 */
@DataJpaTest
@ActiveProfiles("test")
class SettingRepositoryTest {

    @Autowired
    private SettingRepository settingRepository;

    @BeforeEach
    void setUp() {
        settingRepository.deleteAll();
    }

    @Test
    void save_shouldPersistSetting() {
        // Given
        Setting setting = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/123/token")
                .enabled(true)
                .build();

        // When
        Setting saved = settingRepository.save(setting);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDiscordWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/123/token");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_shouldReturnSetting() {
        // Given
        Setting setting = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/456/token")
                .enabled(true)
                .build();
        Setting saved = settingRepository.save(setting);

        // When
        Optional<Setting> found = settingRepository.findById(saved.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getDiscordWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/456/token");
    }

    @Test
    void findFirstByEnabled_shouldReturnEnabledSetting() {
        // Given
        Setting enabled = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/enabled/token")
                .enabled(true)
                .build();
        Setting disabled = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/disabled/token")
                .enabled(false)
                .build();
        settingRepository.save(disabled);
        settingRepository.save(enabled);

        // When
        Optional<Setting> foundEnabled = settingRepository.findFirstByEnabled(true);
        Optional<Setting> foundDisabled = settingRepository.findFirstByEnabled(false);

        // Then
        assertThat(foundEnabled).isPresent();
        assertThat(foundEnabled.get().getEnabled()).isTrue();
        assertThat(foundEnabled.get().getDiscordWebhookUrl()).contains("enabled");

        assertThat(foundDisabled).isPresent();
        assertThat(foundDisabled.get().getEnabled()).isFalse();
        assertThat(foundDisabled.get().getDiscordWebhookUrl()).contains("disabled");
    }

    @Test
    void findFirstByDiscordWebhookUrlIsNotNull_shouldReturnSettingWithWebhook() {
        // Given
        Setting withWebhook = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/789/token")
                .enabled(true)
                .build();
        Setting withoutWebhook = Setting.builder()
                .discordWebhookUrl(null)
                .enabled(true)
                .build();
        settingRepository.save(withoutWebhook);
        settingRepository.save(withWebhook);

        // When
        Optional<Setting> found = settingRepository.findFirstByDiscordWebhookUrlIsNotNull();

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getDiscordWebhookUrl()).isNotNull();
        assertThat(found.get().getDiscordWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/789/token");
    }

    @Test
    void findFirstByDiscordWebhookUrlIsNotNull_shouldReturnEmpty_whenNoWebhook() {
        // Given
        Setting setting = Setting.builder()
                .discordWebhookUrl(null)
                .enabled(true)
                .build();
        settingRepository.save(setting);

        // When
        Optional<Setting> found = settingRepository.findFirstByDiscordWebhookUrlIsNotNull();

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void delete_shouldRemoveSetting() {
        // Given
        Setting setting = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/delete/token")
                .enabled(true)
                .build();
        Setting saved = settingRepository.save(setting);

        // When
        settingRepository.deleteById(saved.getId());

        // Then
        assertThat(settingRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void update_shouldModifySetting() {
        // Given
        Setting setting = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/original/token")
                .enabled(true)
                .build();
        Setting saved = settingRepository.save(setting);

        // When
        saved.setDiscordWebhookUrl("https://discord.com/api/webhooks/updated/token");
        saved.setEnabled(false);
        Setting updated = settingRepository.save(saved);

        // Then
        assertThat(updated.getDiscordWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/updated/token");
        assertThat(updated.getEnabled()).isFalse();
        assertThat(updated.getUpdatedAt()).isAfter(updated.getCreatedAt());
    }

    @Test
    void save_withNullWebhookUrl_shouldPersist() {
        // Given
        Setting setting = Setting.builder()
                .discordWebhookUrl(null)
                .enabled(true)
                .build();

        // When
        Setting saved = settingRepository.save(setting);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDiscordWebhookUrl()).isNull();
        assertThat(saved.getEnabled()).isTrue();
    }

    @Test
    void count_shouldReturnCorrectCount() {
        // Given
        Setting setting1 = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/1/token")
                .enabled(true)
                .build();
        Setting setting2 = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/2/token")
                .enabled(false)
                .build();
        settingRepository.save(setting1);
        settingRepository.save(setting2);

        // When
        long count = settingRepository.count();

        // Then
        assertThat(count).isEqualTo(2);
    }
}
