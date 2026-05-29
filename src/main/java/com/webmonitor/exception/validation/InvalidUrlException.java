package com.webmonitor.exception.validation;

/**
 * 잘못된 URL 예외
 * URL 형식 오류, 빈 URL 등 사용자 입력 URL 검증 실패 시 발생
 * (UrlValidationException은 SSRF 방어용, 이 예외는 기본 형식 검증용)
 */
public class InvalidUrlException extends ValidationException {

    private static final String ERROR_CODE = "VALIDATION_001";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public InvalidUrlException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public InvalidUrlException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * URL 정보를 컨텍스트에 추가하는 편의 메서드
     * @param url 잘못된 URL
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public InvalidUrlException withUrl(String url) {
        addContext("url", url);
        return this;
    }
}
