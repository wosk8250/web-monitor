package com.webmonitor.exception.validation;

/**
 * 잘못된 체크 주기 예외
 * 체크 주기가 음수, 0, 너무 짧음 등 검증 실패 시 발생
 */
public class InvalidIntervalException extends ValidationException {

    private static final String ERROR_CODE = "VALIDATION_003";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public InvalidIntervalException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public InvalidIntervalException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * Interval 정보를 컨텍스트에 추가하는 편의 메서드
     * @param interval 잘못된 주기 (초)
     * @param minAllowed 최소 허용 주기
     * @param maxAllowed 최대 허용 주기
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public InvalidIntervalException withIntervalInfo(Integer interval, Integer minAllowed, Integer maxAllowed) {
        addContext("interval", interval);
        addContext("minAllowed", minAllowed);
        addContext("maxAllowed", maxAllowed);
        return this;
    }
}
