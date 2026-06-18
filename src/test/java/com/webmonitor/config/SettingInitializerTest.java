package com.webmonitor.config;

import com.webmonitor.dto.SettingRequest;
import com.webmonitor.service.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettingInitializerTest {

    @Mock
    private SettingService settingService;

    @InjectMocks
    private SettingInitializer initializer;

    private static final String VALID_URL = "https://discord.com/api/webhooks/123456789/abcToken-xyz";
    private static final DefaultApplicationArguments EMPTY_ARGS =
            new DefaultApplicationArguments();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(initializer, "discordWebhookUrl", VALID_URL);
    }

    @Test
    @DisplayName("Setting이 이미 존재하면 (활성/비활성 무관) createSetting을 호출하지 않는다")
    void run_WhenSettingExists_ShouldSkip() throws Exception {
        when(settingService.hasAnySetting()).thenReturn(true);

        initializer.run(EMPTY_ARGS);

        verify(settingService, never()).createSetting(any());
    }

    @Test
    @DisplayName("DISCORD_WEBHOOK_URL이 blank이면 createSetting을 호출하지 않는다")
    void run_WhenWebhookUrlBlank_ShouldSkip() throws Exception {
        ReflectionTestUtils.setField(initializer, "discordWebhookUrl", "");
        when(settingService.hasAnySetting()).thenReturn(false);

        initializer.run(EMPTY_ARGS);

        verify(settingService, never()).createSetting(any());
    }

    @Test
    @DisplayName("DISCORD_WEBHOOK_URL이 공백 문자열이면 createSetting을 호출하지 않는다")
    void run_WhenWebhookUrlWhitespaceOnly_ShouldSkip() throws Exception {
        ReflectionTestUtils.setField(initializer, "discordWebhookUrl", "   ");
        when(settingService.hasAnySetting()).thenReturn(false);

        initializer.run(EMPTY_ARGS);

        verify(settingService, never()).createSetting(any());
    }

    @Test
    @DisplayName("DISCORD_WEBHOOK_URL 형식이 올바르지 않으면 createSetting을 호출하지 않는다")
    void run_WhenWebhookUrlInvalid_ShouldSkip() throws Exception {
        ReflectionTestUtils.setField(initializer, "discordWebhookUrl", "http://wrong-url.com");
        when(settingService.hasAnySetting()).thenReturn(false);

        initializer.run(EMPTY_ARGS);

        verify(settingService, never()).createSetting(any());
    }

    @Test
    @DisplayName("prefix만 있고 id/token 없는 URL은 createSetting을 호출하지 않는다")
    void run_WhenWebhookUrlPrefixOnly_ShouldSkip() throws Exception {
        ReflectionTestUtils.setField(initializer, "discordWebhookUrl", "https://discord.com/api/webhooks/");
        when(settingService.hasAnySetting()).thenReturn(false);

        initializer.run(EMPTY_ARGS);

        verify(settingService, never()).createSetting(any());
    }

    @Test
    @DisplayName("정상 URL이면 enabled=true로 createSetting을 1회 호출한다")
    void run_WhenValidUrl_ShouldCreateSetting() throws Exception {
        when(settingService.hasAnySetting()).thenReturn(false);

        initializer.run(EMPTY_ARGS);

        ArgumentCaptor<SettingRequest> captor = ArgumentCaptor.forClass(SettingRequest.class);
        verify(settingService).createSetting(captor.capture());
        assertThat(captor.getValue().getDiscordWebhookUrl()).isEqualTo(VALID_URL);
        assertThat(captor.getValue().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("앞뒤 공백이 포함된 유효 URL은 trim 후 createSetting에 전달된다")
    void run_WhenValidUrlWithWhitespace_ShouldCreateSettingWithTrimmedUrl() throws Exception {
        ReflectionTestUtils.setField(initializer, "discordWebhookUrl", "  " + VALID_URL + "  ");
        when(settingService.hasAnySetting()).thenReturn(false);

        initializer.run(EMPTY_ARGS);

        ArgumentCaptor<SettingRequest> captor = ArgumentCaptor.forClass(SettingRequest.class);
        verify(settingService).createSetting(captor.capture());
        assertThat(captor.getValue().getDiscordWebhookUrl()).isEqualTo(VALID_URL);
    }

    @Test
    @DisplayName("createSetting에서 예외가 발생해도 앱 기동이 실패하지 않는다")
    void run_WhenCreateSettingThrows_ShouldNotPropagateException() throws Exception {
        when(settingService.hasAnySetting()).thenReturn(false);
        doThrow(new RuntimeException("DB 오류")).when(settingService).createSetting(any());

        assertThatNoException().isThrownBy(() -> initializer.run(EMPTY_ARGS));
    }

    @Test
    @DisplayName("createSetting에서 DataIntegrityViolationException 발생 시 앱 기동이 실패하지 않는다")
    void run_WhenDuplicateInsert_ShouldNotPropagateException() throws Exception {
        when(settingService.hasAnySetting()).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key")).when(settingService).createSetting(any());

        assertThatNoException().isThrownBy(() -> initializer.run(EMPTY_ARGS));
    }

    @Test
    @DisplayName("hasAnySetting에서 예외가 발생해도 앱 기동이 실패하지 않는다")
    void run_WhenHasAnySettingThrows_ShouldNotPropagateException() throws Exception {
        when(settingService.hasAnySetting()).thenThrow(new RuntimeException("DB 연결 실패"));

        assertThatNoException().isThrownBy(() -> initializer.run(EMPTY_ARGS));
        verify(settingService, never()).createSetting(any());
    }
}
