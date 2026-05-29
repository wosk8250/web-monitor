package com.webmonitor.exception.notification;

/**
 * Discord Webhook 전송 실패 예외
 * Webhook URL 오류, Rate Limit, 네트워크 오류 등으로 Discord 전송 실패 시 발생
 */
public class DiscordWebhookException extends NotificationException {

    private static final String ERROR_CODE = "NOTIFICATION_001";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public DiscordWebhookException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public DiscordWebhookException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * Alert 정보를 컨텍스트에 추가하는 편의 메서드
     * @param alertId 알림 ID
     * @param retryCount 재시도 횟수
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public DiscordWebhookException withAlertInfo(Long alertId, int retryCount) {
        addContext("alertId", alertId);
        addContext("retryCount", retryCount);
        return this;
    }

    /**
     * HTTP 상태 코드를 컨텍스트에 추가하는 편의 메서드
     * @param statusCode HTTP 상태 코드
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public DiscordWebhookException withHttpStatus(int statusCode) {
        addContext("httpStatusCode", statusCode);
        return this;
    }
}
