package com.webmonitor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseService 단위 테스트")
class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService(new ObjectMapper());
    }

    @Test
    @DisplayName("subscribe - 정상 케이스: SseEmitter 반환")
    void subscribe_returnsEmitter() {
        SseEmitter emitter = sseService.subscribe("client1");
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("subscribe - 클라이언트 수 1 증가")
    void subscribe_incrementsClientCount() {
        sseService.subscribe("client1");
        assertThat(sseService.getConnectedClientsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("subscribe - 동일 clientId 재구독 시 클라이언트 수 1 유지")
    void subscribe_sameClientId_countStaysOne() {
        sseService.subscribe("client1");
        sseService.subscribe("client1");

        assertThat(sseService.getConnectedClientsCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("subscribe - 여러 클라이언트 구독 시 카운트 반영")
    void subscribe_multipleClients_countsAll() {
        sseService.subscribe("client1");
        sseService.subscribe("client2");
        sseService.subscribe("client3");

        assertThat(sseService.getConnectedClientsCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("sendAlertToClient - 알 수 없는 clientId: 예외 없이 종료")
    void sendAlertToClient_unknownClientId_noException() {
        Alert alert = buildAlert();

        assertThatCode(() -> sseService.sendAlertToClient("nonexistent", alert))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendAlertToClient - 구독된 clientId: 예외 없이 전송")
    void sendAlertToClient_knownClientId_noException() {
        sseService.subscribe("client1");
        Alert alert = buildAlert();

        assertThatCode(() -> sseService.sendAlertToClient("client1", alert))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("broadcastAlert - 연결된 클라이언트 없음: 예외 없이 종료")
    void broadcastAlert_noClients_noException() {
        Alert alert = buildAlert();

        assertThatCode(() -> sseService.broadcastAlert(alert))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("broadcastAlert - 연결된 클라이언트 있음: 예외 없이 전송 완료")
    void broadcastAlert_withClients_noException() {
        sseService.subscribe("client1");
        sseService.subscribe("client2");
        Alert alert = buildAlert();

        assertThatCode(() -> sseService.broadcastAlert(alert))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getConnectedClientsCount - 초기 상태: 0 반환")
    void getConnectedClientsCount_initially_returnsZero() {
        assertThat(sseService.getConnectedClientsCount()).isZero();
    }

    @Test
    @DisplayName("closeAllConnections - 모든 연결 종료 후 카운트 0")
    void closeAllConnections_resetsCount() {
        sseService.subscribe("client1");
        sseService.subscribe("client2");

        sseService.closeAllConnections();

        assertThat(sseService.getConnectedClientsCount()).isZero();
    }

    @Test
    @DisplayName("closeAllConnections - 빈 상태에서 호출 시 예외 없음")
    void closeAllConnections_whenEmpty_noException() {
        assertThatCode(() -> sseService.closeAllConnections())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("subscribe - onCompletion 콜백 발화 시 카운트 감소")
    void subscribe_onCompletionCallback_decrementsCount() {
        // SseEmitter.complete()는 HTTP 핸들러 없이 콜백을 발화하지 않으므로
        // ReflectionTestUtils로 콜백을 직접 호출해 단위 테스트에서 검증
        SseEmitter emitter = sseService.subscribe("client1");
        assertThat(sseService.getConnectedClientsCount()).isEqualTo(1);

        Runnable callback = (Runnable) ReflectionTestUtils.getField(emitter, "completionCallback");
        assertThat(callback).isNotNull();
        callback.run();

        assertThat(sseService.getConnectedClientsCount()).isZero();
    }

    @Test
    @DisplayName("subscribe - 재구독 후 기존 emitter의 onCompletion이 늦게 발화해도 새 emitter 유지")
    void subscribe_resubscribe_lateCallbackDoesNotRemoveNewEmitter() {
        // 기존 emitter가 교체된 후 onCompletion 콜백이 늦게 발화하는 race condition 시뮬레이션
        // conditional remove(key, value)로 수정됐으므로 새 emitter는 제거되지 않아야 함
        SseEmitter first = sseService.subscribe("client1");
        sseService.subscribe("client1"); // 재구독 → first 교체

        Runnable firstCallback = (Runnable) ReflectionTestUtils.getField(first, "completionCallback");
        assertThat(firstCallback).isNotNull();
        firstCallback.run(); // 교체된 emitter의 콜백이 뒤늦게 발화

        assertThat(sseService.getConnectedClientsCount()).isEqualTo(1);
    }

    private Alert buildAlert() {
        Site site = Site.builder()
                .name("테스트")
                .url("https://test.com")
                .active(true)
                .build();

        return Alert.builder()
                .site(site)
                .alertType(Alert.AlertType.KEYWORD)
                .message("테스트 알림")
                .detectedUrl("https://test.com")
                .detectedAt(LocalDateTime.now())
                .sent(false)
                .priority(Alert.Priority.NORMAL)
                .build();
    }
}
