package com.webmonitor.listener;

import com.webmonitor.event.SseTransmissionFailureEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE 전송 실패 이벤트 리스너
 * SSE 전송 실패를 추적하고 모니터링 시스템에서 알림을 받을 수 있도록 함
 */
@Component
@Slf4j
public class SseFailureEventListener {

    // SSE 전송 실패 카운터 (모니터링용)
    private final AtomicLong failureCount = new AtomicLong(0);

    @EventListener
    public void handleSseTransmissionFailure(SseTransmissionFailureEvent event) {
        long currentFailureCount = failureCount.incrementAndGet();

        // ERROR 레벨로 로깅하여 모니터링 시스템에서 알림 받을 수 있도록 함
        log.error("[SSE 전송 실패] 알림 ID: {}, 메시지: {}, 메서드: {}, 오류: {}, 총 실패 횟수: {}",
                event.getAlertId(),
                event.getAlertMessage(),
                event.getMethodName(),
                event.getErrorMessage(),
                currentFailureCount);

        // TODO: 필요 시 여기에 추가 처리 로직 구현 가능
        // - Slack/Discord 알림 전송
        // - 메트릭 시스템에 카운터 업데이트
        // - 재시도 큐에 추가 등
    }

    /**
     * 현재까지의 SSE 전송 실패 횟수 조회 (모니터링용)
     * @return 실패 횟수
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    /**
     * 실패 카운터 초기화 (테스트용)
     */
    public void resetFailureCount() {
        failureCount.set(0);
    }
}
