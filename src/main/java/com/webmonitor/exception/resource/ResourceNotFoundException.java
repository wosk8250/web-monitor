package com.webmonitor.exception.resource;

import com.webmonitor.exception.WebMonitorException;

/**
 * 리소스를 찾을 수 없음 예외의 Base Exception
 * Site, Keyword, Alert, Product 등 엔티티 조회 실패 시 사용
 */
public abstract class ResourceNotFoundException extends WebMonitorException {

    /**
     * 생성자 - 메시지와 에러 코드
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     */
    protected ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * 생성자 - 메시지, 에러 코드, 원인 예외
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     * @param cause 원인 예외
     */
    protected ResourceNotFoundException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
