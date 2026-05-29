package com.webmonitor.exception.crawling;

/**
 * Circuit Breaker OPEN 상태 예외
 * Resilience4j Circuit Breaker가 OPEN 상태일 때 발생
 * 연속된 실패로 인해 일시적으로 서비스 호출이 차단됨
 */
public class CircuitBreakerOpenException extends CrawlingException {

    private static final String ERROR_CODE = "CRAWLING_004";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public CircuitBreakerOpenException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외 (CallNotPermittedException)
     */
    public CircuitBreakerOpenException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * Circuit Breaker 정보를 컨텍스트에 추가하는 편의 메서드
     * @param circuitBreakerName Circuit Breaker 이름 (예: "webCrawler", "discord")
     * @param waitDurationMs 대기 시간 (밀리초)
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public CircuitBreakerOpenException withCircuitBreakerInfo(String circuitBreakerName, long waitDurationMs) {
        addContext("circuitBreakerName", circuitBreakerName);
        addContext("waitDurationMs", waitDurationMs);
        return this;
    }
}
