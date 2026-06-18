package com.webmonitor.controller;

import com.webmonitor.domain.Setting;
import com.webmonitor.dto.SettingRequest;
import com.webmonitor.repository.SettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("SettingController 통합 테스트")
class SettingControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SettingRepository settingRepository;

    private Setting testSetting;

    @BeforeEach
    void setUp() {
        settingRepository.deleteAll();

        testSetting = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/123/token")
                .enabled(true)
                .build();
        testSetting = settingRepository.save(testSetting);
    }

    @Test
    @DisplayName("GET /api/settings - 전체 설정 조회: 200 반환")
    void getAllSettings_returns200() {
        ResponseEntity<Setting[]> response = restTemplate.getForEntity(
                "/api/settings", Setting[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("GET /api/settings/{id} - 존재하는 ID: 200 반환")
    void getSettingById_existingId_returns200() {
        ResponseEntity<Setting> response = restTemplate.getForEntity(
                "/api/settings/" + testSetting.getId(), Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(testSetting.getId());
    }

    @Test
    @DisplayName("GET /api/settings/{id} - 존재하지 않는 ID: 404 반환")
    void getSettingById_nonExistentId_returns404() {
        ResponseEntity<Setting> response = restTemplate.getForEntity(
                "/api/settings/99999", Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/settings?enabled=true - 활성화된 설정 있음: 200 반환")
    void getSettingsEnabledTrue_settingExists_returns200() {
        ResponseEntity<Setting[]> response = restTemplate.getForEntity(
                "/api/settings?enabled=true", Setting[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()[0].getEnabled()).isTrue();
    }

    @Test
    @DisplayName("GET /api/settings?enabled=true - 활성 설정 없음: 200 빈 리스트 반환")
    void getSettingsEnabledTrue_noActiveSetting_returnsEmptyList() {
        settingRepository.deleteAll();
        Setting disabled = Setting.builder().enabled(false).build();
        settingRepository.save(disabled);

        ResponseEntity<Setting[]> response = restTemplate.getForEntity(
                "/api/settings?enabled=true", Setting[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/settings - 새 설정 생성: 201 반환")
    void createSetting_returns201() {
        settingRepository.deleteAll();

        Setting newSetting = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/456/token2")
                .enabled(false)
                .build();

        ResponseEntity<Setting> response = restTemplate.postForEntity(
                "/api/settings", newSetting, Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isNotNull();
    }

    @Test
    @DisplayName("PATCH /api/settings/{id} - 설정 수정: 200 반환 및 값 반영")
    void updateSetting_returns200WithUpdatedValues() {
        Setting update = Setting.builder()
                .discordWebhookUrl("https://discord.com/api/webhooks/updated/token")
                .enabled(false)
                .build();

        ResponseEntity<Setting> response = restTemplate.exchange(
                "/api/settings/" + testSetting.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(update),
                Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("PATCH /api/settings/{id} - 존재하지 않는 ID: 404 반환")
    void updateSetting_nonExistentId_returns404() {
        Setting update = Setting.builder().enabled(false).build();

        ResponseEntity<Setting> response = restTemplate.exchange(
                "/api/settings/99999",
                HttpMethod.PATCH,
                new HttpEntity<>(update),
                Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("DELETE /api/settings/{id} - 설정 삭제: 204 반환")
    void deleteSetting_returns204() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/settings/" + testSetting.getId(),
                HttpMethod.DELETE,
                null,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(settingRepository.existsById(testSetting.getId())).isFalse();
    }

    @Test
    @DisplayName("DELETE /api/settings/{id} - 존재하지 않는 ID: 404 반환")
    void deleteSetting_nonExistentId_returns404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/settings/99999",
                HttpMethod.DELETE,
                null,
                Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PATCH /api/settings/{id} - enabled=false로 비활성화: 200 반환")
    void patchSettingEnabled_setFalse_returns200() {
        SettingRequest patch = new SettingRequest(null, false);

        ResponseEntity<Setting> response = restTemplate.exchange(
                "/api/settings/" + testSetting.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEnabled()).isFalse();
    }

    @Test
    @DisplayName("PATCH /api/settings/{id} - enabled 명시적 설정: 최종 상태 반영")
    void patchSettingEnabled_setThenUnset_returnsExpectedState() {
        SettingRequest patchFalse = new SettingRequest(null, false);
        restTemplate.exchange("/api/settings/" + testSetting.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patchFalse), Setting.class);

        SettingRequest patchTrue = new SettingRequest(null, true);
        ResponseEntity<Setting> secondResponse = restTemplate.exchange(
                "/api/settings/" + testSetting.getId(), HttpMethod.PATCH,
                new HttpEntity<>(patchTrue), Setting.class);

        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/settings/{id} - discordWebhookUrl 빈 문자열: 기존 URL 유지")
    void patchSetting_emptyWebhookUrl_doesNotUpdateWebhookUrl() {
        SettingRequest patch = new SettingRequest("", null);

        ResponseEntity<Setting> response = restTemplate.exchange(
                "/api/settings/" + testSetting.getId(),
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDiscordWebhookUrl())
                .isEqualTo("https://discord.com/api/webhooks/123/token");
    }

    @Test
    @DisplayName("PATCH /api/settings/{id} - 존재하지 않는 ID: 404 반환")
    void patchSettingEnabled_nonExistentId_returns404() {
        SettingRequest patch = new SettingRequest(null, false);

        ResponseEntity<Setting> response = restTemplate.exchange(
                "/api/settings/99999",
                HttpMethod.PATCH,
                new HttpEntity<>(patch),
                Setting.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
