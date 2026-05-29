package com.webmonitor.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 중요 시스템 오류 이벤트
 * GlobalExceptionHandler에서 발행되어 CriticalErrorNotifier가 수신
 */
@Getter
public class CriticalErrorEvent extends ApplicationEvent {

    private final String exceptionType;
    private final String location;

    public CriticalErrorEvent(Object source, String exceptionType, String location) {
        super(source);
        this.exceptionType = exceptionType;
        this.location = location;
    }
}
