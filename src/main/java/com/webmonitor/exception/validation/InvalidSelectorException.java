package com.webmonitor.exception.validation;

/**
 * 잘못된 CSS Selector 예외
 * CSS Selector 문법 오류, 빈 selector 등 검증 실패 시 발생
 */
public class InvalidSelectorException extends ValidationException {

    private static final String ERROR_CODE = "VALIDATION_002";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public InvalidSelectorException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public InvalidSelectorException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * Selector 정보를 컨텍스트에 추가하는 편의 메서드
     * @param selector 잘못된 Selector
     * @param selectorType Selector 타입 (예: "articleSelector", "stockStatusSelector")
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public InvalidSelectorException withSelector(String selector, String selectorType) {
        addContext("selector", selector);
        addContext("selectorType", selectorType);
        return this;
    }
}
