package com.webmonitor.exception.notification;

import com.webmonitor.exception.WebMonitorException;

/**
 * 알림 전송 관련 예외의 Base Exception
 * Discord Webhook, SSE 등 알림 전송 프로세스의 모든 예외를 포함
 */
public abstract class NotificationException extends WebMonitorException {

    /**
     * 생성자 - 메시지와 에러 코드
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     */
    protected NotificationException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * 생성자 - 메시지, 에러 코드, 원인 예외
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     * @param cause 원인 예외
     */
    protected NotificationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
