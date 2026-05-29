package com.webmonitor.exception.validation;

import com.webmonitor.exception.WebMonitorException;

/**
 * 유효성 검증 관련 예외의 Base Exception
 * URL, Selector, Interval 등 입력값 검증 실패 시 사용
 */
public abstract class ValidationException extends WebMonitorException {

    /**
     * 생성자 - 메시지와 에러 코드
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     */
    protected ValidationException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * 생성자 - 메시지, 에러 코드, 원인 예외
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     * @param cause 원인 예외
     */
    protected ValidationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
