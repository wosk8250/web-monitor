package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Product;
import com.webmonitor.domain.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiscordService 단위 테스트")
class DiscordServiceTest {

    @Mock
    private DiscordWebhookCaller discordWebhookCaller;

    @InjectMocks
    private DiscordService discordService;

    private static final String VALID_WEBHOOK = "https://discord.com/api/webhooks/123456789/token";

    private Alert testAlert;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        Site site = Site.builder()
                .name("테스트 사이트")
                .url("https://test.com")
                .active(true)
                .build();

        testAlert = Alert.builder()
                .site(site)
                .alertType(Alert.AlertType.KEYWORD)
                .message("테스트 알림")
                .detectedUrl("https://test.com/page")
                .detectedAt(LocalDateTime.now())
                .sent(false)
                .priority(Alert.Priority.NORMAL)
                .build();
        ReflectionTestUtils.setField(testAlert, "id", 1L);

        testProduct = Product.builder()
                .name("테스트 제품")
                .url("https://shop.com/product")
                .currentStatus(Product.StockStatus.IN_STOCK)
                .priority(Product.Priority.NORMAL)
                .active(true)
                .build();
    }

    // ========== sendAlert Tests ==========

    @Test
    @DisplayName("sendAlert - 유효한 URL + 성공 응답: webhookCaller 1회 호출")
    void sendAlert_validUrl_callsWebhookCallerOnce() {
        when(discordWebhookCaller.sendDiscordWebhookWithRetry(anyString(), any(), anyString()))
                .thenReturn(ResponseEntity.ok(""));

        discordService.sendAlert(VALID_WEBHOOK, testAlert);

        verify(discordWebhookCaller, times(1)).sendDiscordWebhookWithRetry(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("sendAlert - null URL: webhookCaller 미호출")
    void sendAlert_nullUrl_doesNotCallWebhookCaller() {
        discordService.sendAlert(null, testAlert);

        verifyNoInteractions(discordWebhookCaller);
    }

    @Test
    @DisplayName("sendAlert - 빈 URL: webhookCaller 미호출")
    void sendAlert_emptyUrl_doesNotCallWebhookCaller() {
        discordService.sendAlert("   ", testAlert);

        verifyNoInteractions(discordWebhookCaller);
    }

    @Test
    @DisplayName("sendAlert - 유효하지 않은 URL 형식: webhookCaller 미호출")
    void sendAlert_invalidWebhookFormat_doesNotCallWebhookCaller() {
        discordService.sendAlert("https://notdiscord.com/hook", testAlert);

        verifyNoInteractions(discordWebhookCaller);
    }

    @Test
    @DisplayName("sendAlert - webhookCaller 예외: RuntimeException 전파")
    void sendAlert_webhookCallerThrows_propagatesException() {
        when(discordWebhookCaller.sendDiscordWebhookWithRetry(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("Discord 오류"));

        assertThatThrownBy(() -> discordService.sendAlert(VALID_WEBHOOK, testAlert))
                .isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("sendAlert - 요청 바디에 alert title/url 포함 검증")
    void sendAlert_requestBodyContainsAlertContent() {
        when(discordWebhookCaller.sendDiscordWebhookWithRetry(anyString(), any(), anyString()))
                .thenReturn(ResponseEntity.ok(""));

        discordService.sendAlert(VALID_WEBHOOK, testAlert);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(discordWebhookCaller).sendDiscordWebhookWithRetry(anyString(), captor.capture(), anyString());
        Map<String, Object> body = captor.getValue().getBody();
        List<Map<String, Object>> embeds = (List<Map<String, Object>>) body.get("embeds");
        assertThat(embeds).hasSize(1);
        assertThat(embeds.get(0).get("title")).isEqualTo(testAlert.getMessage());
        assertThat(embeds.get(0).get("url")).isEqualTo(testAlert.getDetectedUrl());
    }

    // ========== sendProductRestockAlert Tests ==========

    @Test
    @DisplayName("sendProductRestockAlert - 정상 케이스: webhookCaller 1회 호출")
    void sendProductRestockAlert_happyPath_callsWebhookCaller() {
        when(discordWebhookCaller.sendDiscordWebhookWithRetry(anyString(), any(), anyString()))
                .thenReturn(ResponseEntity.ok(""));

        discordService.sendProductRestockAlert(VALID_WEBHOOK, testProduct);

        verify(discordWebhookCaller, times(1)).sendDiscordWebhookWithRetry(anyString(), any(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("sendProductRestockAlert - 요청 바디에 제품명 포함 검증")
    void sendProductRestockAlert_requestBodyContainsProductName() {
        when(discordWebhookCaller.sendDiscordWebhookWithRetry(anyString(), any(), anyString()))
                .thenReturn(ResponseEntity.ok(""));

        discordService.sendProductRestockAlert(VALID_WEBHOOK, testProduct);

        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(discordWebhookCaller).sendDiscordWebhookWithRetry(anyString(), captor.capture(), anyString());
        Map<String, Object> body = captor.getValue().getBody();
        List<Map<String, Object>> embeds = (List<Map<String, Object>>) body.get("embeds");
        assertThat(embeds).hasSize(1);
        assertThat(embeds.get(0).get("title").toString()).contains(testProduct.getName());
        assertThat(embeds.get(0).get("url")).isEqualTo(testProduct.getUrl());
    }

    @Test
    @DisplayName("sendProductRestockAlert - imageUrl 없음: 예외 없이 전송")
    void sendProductRestockAlert_noImageUrl_noException() {
        testProduct.setImageUrl(null);
        when(discordWebhookCaller.sendDiscordWebhookWithRetry(anyString(), any(), anyString()))
                .thenReturn(ResponseEntity.ok(""));

        assertThatCode(() -> discordService.sendProductRestockAlert(VALID_WEBHOOK, testProduct))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendProductRestockAlert - 빈 URL: webhookCaller 미호출")
    void sendProductRestockAlert_emptyUrl_doesNotCallWebhookCaller() {
        discordService.sendProductRestockAlert("", testProduct);

        verifyNoInteractions(discordWebhookCaller);
    }

    // ========== sendSimpleMessage Tests ==========

    @Test
    @DisplayName("sendSimpleMessage - 정상 케이스: webhookCaller 1회 호출")
    void sendSimpleMessage_happyPath_callsWebhookCaller() {
        when(discordWebhookCaller.sendDiscordWebhookWithRetry(anyString(), any(), anyString()))
                .thenReturn(ResponseEntity.ok(""));

        discordService.sendSimpleMessage(VALID_WEBHOOK, "테스트 메시지");

        verify(discordWebhookCaller, times(1)).sendDiscordWebhookWithRetry(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("sendSimpleMessage - 빈 URL: webhookCaller 미호출")
    void sendSimpleMessage_emptyUrl_doesNotCallWebhookCaller() {
        discordService.sendSimpleMessage("", "메시지");

        verifyNoInteractions(discordWebhookCaller);
    }
}
