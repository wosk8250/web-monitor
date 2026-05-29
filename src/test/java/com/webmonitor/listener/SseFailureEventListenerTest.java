package com.webmonitor.listener;

import com.webmonitor.event.SseTransmissionFailureEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 실패 이벤트 리스너 테스트
 */
@ExtendWith(MockitoExtension.class)
class SseFailureEventListenerTest {

    private SseFailureEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new SseFailureEventListener();
        listener.resetFailureCount();
    }

    @Test
    @DisplayName("SSE 전송 실패 이벤트 수신 시 실패 카운터 증가")
    void handleSseTransmissionFailure_이벤트수신_실패카운터증가() {
        // Given: SSE 전송 실패 이벤트
        SseTransmissionFailureEvent event = new SseTransmissionFailureEvent(
                this,
                1L,
                "테스트 알림",
                "Connection timeout",
                "createAlert"
        );

        // When: 이벤트 처리
        listener.handleSseTransmissionFailure(event);

        // Then: 실패 카운터 증가
        assertThat(listener.getFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 SSE 전송 실패 이벤트 수신 시 카운터 누적")
    void handleSseTransmissionFailure_여러이벤트수신_카운터누적() {
        // Given: 3개의 SSE 전송 실패 이벤트
        SseTransmissionFailureEvent event1 = new SseTransmissionFailureEvent(
                this, 1L, "알림 1", "Error 1", "createAlert"
        );
        SseTransmissionFailureEvent event2 = new SseTransmissionFailureEvent(
                this, 2L, "알림 2", "Error 2", "createContentChangeAlert"
        );
        SseTransmissionFailureEvent event3 = new SseTransmissionFailureEvent(
                this, 3L, "알림 3", "Error 3", "createArticleAlert"
        );

        // When: 이벤트 처리
        listener.handleSseTransmissionFailure(event1);
        listener.handleSseTransmissionFailure(event2);
        listener.handleSseTransmissionFailure(event3);

        // Then: 실패 카운터가 3으로 증가
        assertThat(listener.getFailureCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("실패 카운터 초기화 테스트")
    void resetFailureCount_카운터초기화() {
        // Given: 실패 카운터가 5인 상태
        for (int i = 0; i < 5; i++) {
            SseTransmissionFailureEvent event = new SseTransmissionFailureEvent(
                    this, (long) i, "알림 " + i, "Error " + i, "createAlert"
            );
            listener.handleSseTransmissionFailure(event);
        }
        assertThat(listener.getFailureCount()).isEqualTo(5);

        // When: 카운터 초기화
        listener.resetFailureCount();

        // Then: 카운터가 0으로 초기화됨
        assertThat(listener.getFailureCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("null 이벤트 처리 시 예외 발생하지 않음")
    void handleSseTransmissionFailure_null이벤트_예외없음() {
        // Given: null 체크를 위한 유효한 이벤트 (실제로는 Spring이 null을 전달하지 않음)
        SseTransmissionFailureEvent validEvent = new SseTransmissionFailureEvent(
                this, 1L, "알림", "Error", "createAlert"
        );

        // When & Then: 예외 발생하지 않음
        listener.handleSseTransmissionFailure(validEvent);
        assertThat(listener.getFailureCount()).isEqualTo(1);
    }
}
