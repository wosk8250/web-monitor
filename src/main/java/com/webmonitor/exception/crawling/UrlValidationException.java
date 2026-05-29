package com.webmonitor.exception.crawling;

/**
 * URL 검증 실패 예외
 * SSRF 방어, 프로토콜 검증, 포트 제한 등 URL 유효성 검증 실패 시 발생
 */
public class UrlValidationException extends CrawlingException {

    private static final String ERROR_CODE = "CRAWLING_001";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public UrlValidationException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public UrlValidationException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * URL 정보를 컨텍스트에 추가하는 편의 메서드
     * @param url 검증 실패한 URL
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public UrlValidationException withUrl(String url) {
        addContext("url", url);
        return this;
    }
}
