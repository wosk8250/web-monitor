package com.webmonitor.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * SSE 실시간 알림 전송 실패 이벤트
 * MonitorService에서 SSE 전송 실패 시 발행되어 모니터링 시스템에서 추적 가능
 */
@Getter
public class SseTransmissionFailureEvent extends ApplicationEvent {

    private final Long alertId;
    private final String alertMessage;
    private final String errorMessage;
    private final String methodName;

    public SseTransmissionFailureEvent(Object source, Long alertId, String alertMessage,
                                       String errorMessage, String methodName) {
        super(source);
        this.alertId = alertId;
        this.alertMessage = alertMessage;
        this.errorMessage = errorMessage;
        this.methodName = methodName;
    }
}
