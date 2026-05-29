package com.webmonitor.exception.crawling;

import com.webmonitor.exception.WebMonitorException;

/**
 * 웹 크롤링 관련 예외의 Base Exception
 * URL 검증, 크롤링 실패, Rate Limit 등 크롤링 프로세스의 모든 예외를 포함
 */
public abstract class CrawlingException extends WebMonitorException {

    /**
     * 생성자 - 메시지와 에러 코드
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     */
    protected CrawlingException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * 생성자 - 메시지, 에러 코드, 원인 예외
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     * @param cause 원인 예외
     */
    protected CrawlingException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
