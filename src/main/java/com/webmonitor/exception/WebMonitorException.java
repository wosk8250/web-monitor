package com.webmonitor.exception;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Web Monitor 애플리케이션의 모든 커스텀 예외의 Base Exception
 * 에러 코드와 컨텍스트 정보를 포함하여 디버깅과 로깅을 개선
 */
@Getter
public abstract class WebMonitorException extends RuntimeException {

    /**
     * 에러 코드 (예: "CRAWLING_001", "VALIDATION_002" 등)
     */
    private final String errorCode;

    /**
     * 추가 컨텍스트 정보 (예: URL, siteId, keywordId 등)
     */
    private final Map<String, Object> context;

    /**
     * 생성자 - 메시지와 에러 코드
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     */
    protected WebMonitorException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    /**
     * 생성자 - 메시지, 에러 코드, 원인 예외
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     * @param cause 원인 예외
     */
    protected WebMonitorException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    /**
     * 컨텍스트 정보 추가 (Builder 패턴)
     * @param key 컨텍스트 키
     * @param value 컨텍스트 값
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public WebMonitorException addContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    /**
     * 전체 컨텍스트 맵 조회
     * @return 컨텍스트 맵 (읽기 전용 복사본)
     */
    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }

    /**
     * 에러 코드 조회
     * @return 에러 코드
     */
    public String getErrorCode() {
        return errorCode;
    }
}
