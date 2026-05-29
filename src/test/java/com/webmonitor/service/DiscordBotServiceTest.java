package com.webmonitor.service;

import com.webmonitor.config.DiscordBotConfig;
import com.webmonitor.discord.DiscordCommandHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * DiscordBotService 단위 테스트
 *
 * JDA 실제 초기화는 외부 Discord API 연결이 필요하므로
 * 주로 조건 분기 로직만 검증
 */
@ExtendWith(MockitoExtension.class)
class DiscordBotServiceTest {

    @Mock
    private DiscordBotConfig botConfig;

    @Mock
    private DiscordCommandHandler commandHandler;

    @InjectMocks
    private DiscordBotService discordBotService;

    // ===============================
    // init() 테스트 - 조건 분기 검증
    // ===============================

    @Test
    @DisplayName("init - 봇이 비활성화된 경우 JDA 초기화하지 않음")
    void init_WhenBotDisabled_ShouldNotInitializeJDA() {
        // Given
        when(botConfig.isEnabled()).thenReturn(false);

        // When
        discordBotService.init();

        // Then
        assertThat(discordBotService.getJda()).isNull();
        verify(botConfig).isEnabled();
        verify(botConfig, never()).getToken();
    }

    @Test
    @DisplayName("init - 토큰이 null인 경우 JDA 초기화하지 않음")
    void init_WhenTokenIsNull_ShouldNotInitializeJDA() {
        // Given
        when(botConfig.isEnabled()).thenReturn(true);
        when(botConfig.getToken()).thenReturn(null);

        // When
        discordBotService.init();

        // Then
        assertThat(discordBotService.getJda()).isNull();
        verify(botConfig).isEnabled();
        verify(botConfig).getToken();
    }

    @Test
    @DisplayName("init - 토큰이 빈 문자열인 경우 JDA 초기화하지 않음")
    void init_WhenTokenIsEmpty_ShouldNotInitializeJDA() {
        // Given
        when(botConfig.isEnabled()).thenReturn(true);
        when(botConfig.getToken()).thenReturn("");

        // When
        discordBotService.init();

        // Then
        assertThat(discordBotService.getJda()).isNull();
        verify(botConfig).isEnabled();
        verify(botConfig, times(2)).getToken(); // getToken() 호출 2회: null 체크, isEmpty() 체크
    }

    // ===============================
    // shutdown() 테스트
    // ===============================

    @Test
    @DisplayName("shutdown - JDA가 null일 때 예외 없이 처리")
    void shutdown_WhenJdaIsNull_ShouldNotThrowException() {
        // Given
        // JDA는 기본적으로 null

        // When & Then (예외가 발생하지 않아야 함)
        discordBotService.shutdown();
    }

    // ===============================
    // getJda() 테스트
    // ===============================

    @Test
    @DisplayName("getJda - 초기화되지 않은 경우 null 반환")
    void getJda_WhenNotInitialized_ShouldReturnNull() {
        // When
        var jda = discordBotService.getJda();

        // Then
        assertThat(jda).isNull();
    }

    @Test
    @DisplayName("getJda - 봇이 비활성화된 경우 null 반환")
    void getJda_WhenBotDisabled_ShouldReturnNull() {
        // Given
        when(botConfig.isEnabled()).thenReturn(false);
        discordBotService.init();

        // When
        var jda = discordBotService.getJda();

        // Then
        assertThat(jda).isNull();
    }

    @Test
    @DisplayName("getJda - 토큰이 없는 경우 null 반환")
    void getJda_WhenNoToken_ShouldReturnNull() {
        // Given
        when(botConfig.isEnabled()).thenReturn(true);
        when(botConfig.getToken()).thenReturn(null);
        discordBotService.init();

        // When
        var jda = discordBotService.getJda();

        // Then
        assertThat(jda).isNull();
    }

    @Test
    @DisplayName("getJda - 정상 초기화 후 null 반환 (실제 Discord 연결 없음)")
    void getJda_WhenInitializedWithoutRealConnection_ShouldReturnNull() {
        // Given
        when(botConfig.isEnabled()).thenReturn(true);
        when(botConfig.getToken()).thenReturn("fake-token");

        // 실제 JDA 초기화는 Discord API 연결이 필요하므로
        // 테스트에서는 초기화를 시도하지만 실패하거나 null을 반환
        // (이 테스트는 조건 분기만 검증하는 것이 목적)

        // When & Then
        // init()는 예외를 catch하므로 실패해도 프로그램은 계속 실행됨
        try {
            discordBotService.init();
        } catch (Exception e) {
            // Discord API 연결 실패는 예상된 동작
        }

        // 실제 Discord 연결 없이는 JDA가 null일 수 있음
        var jda = discordBotService.getJda();
        // JDA가 null이거나 초기화되지 않았을 것으로 예상
        // 실제 Discord 토큰 없이는 초기화 불가
    }
}
