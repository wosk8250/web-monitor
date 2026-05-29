package com.webmonitor.exception.notification;

/**
 * SSE (Server-Sent Events) 알림 전송 실패 예외
 * SSE 브로드캐스트 실패 시 발생
 */
public class SseNotificationException extends NotificationException {

    private static final String ERROR_CODE = "NOTIFICATION_002";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public SseNotificationException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public SseNotificationException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * Alert 타입을 컨텍스트에 추가하는 편의 메서드
     * @param alertType 알림 타입 (예: "KEYWORD", "PRODUCT_RESTOCK")
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public SseNotificationException withAlertType(String alertType) {
        addContext("alertType", alertType);
        return this;
    }
}
