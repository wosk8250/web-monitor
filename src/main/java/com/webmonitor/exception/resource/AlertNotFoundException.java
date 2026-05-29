package com.webmonitor.exception.resource;

/**
 * 알림을 찾을 수 없음 예외
 * Alert ID로 조회 실패 시 발생
 */
public class AlertNotFoundException extends ResourceNotFoundException {

    private static final String ERROR_CODE = "RESOURCE_003";

    /**
     * 생성자 - Alert ID
     * @param alertId 알림 ID
     */
    public AlertNotFoundException(Long alertId) {
        super(String.format("알림을 찾을 수 없습니다. (ID: %d)", alertId), ERROR_CODE);
        addContext("alertId", alertId);
    }

    /**
     * 생성자 - 커스텀 메시지
     * @param message 에러 메시지
     */
    public AlertNotFoundException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - Alert ID와 원인 예외
     * @param alertId 알림 ID
     * @param cause 원인 예외
     */
    public AlertNotFoundException(Long alertId, Throwable cause) {
        super(String.format("알림을 찾을 수 없습니다. (ID: %d)", alertId), ERROR_CODE, cause);
        addContext("alertId", alertId);
    }
}
