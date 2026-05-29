package com.webmonitor.exception.crawling;

/**
 * Rate Limit 초과 예외
 * 크롤링 속도 제한 초과, Discord API Rate Limit 등 발생 시 사용
 */
public class RateLimitExceededException extends CrawlingException {

    private static final String ERROR_CODE = "CRAWLING_003";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public RateLimitExceededException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * Rate Limit 정보를 컨텍스트에 추가하는 편의 메서드
     * @param retryAfterMs 재시도 대기 시간 (밀리초)
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public RateLimitExceededException withRetryAfter(long retryAfterMs) {
        addContext("retryAfterMs", retryAfterMs);
        return this;
    }
}
