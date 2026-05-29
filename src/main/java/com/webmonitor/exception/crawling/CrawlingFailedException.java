package com.webmonitor.exception.crawling;

/**
 * 웹 크롤링 실패 예외
 * 네트워크 오류, 타임아웃, HTTP 에러 등 크롤링 프로세스 실패 시 발생
 */
public class CrawlingFailedException extends CrawlingException {

    private static final String ERROR_CODE = "CRAWLING_002";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public CrawlingFailedException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외 (IOException, SocketTimeoutException 등)
     */
    public CrawlingFailedException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * URL과 재시도 횟수를 컨텍스트에 추가하는 편의 메서드
     * @param url 크롤링 실패한 URL
     * @param retryCount 재시도 횟수
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public CrawlingFailedException withDetails(String url, int retryCount) {
        addContext("url", url);
        addContext("retryCount", retryCount);
        return this;
    }
}
