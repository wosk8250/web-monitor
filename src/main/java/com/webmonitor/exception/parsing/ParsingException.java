package com.webmonitor.exception.parsing;

import com.webmonitor.exception.WebMonitorException;

/**
 * 파싱 관련 예외의 Base Exception
 * HTML 파싱, 제품 정보 추출, 게시글 추출 등 파싱 프로세스의 모든 예외를 포함
 */
public abstract class ParsingException extends WebMonitorException {

    /**
     * 생성자 - 메시지와 에러 코드
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     */
    protected ParsingException(String message, String errorCode) {
        super(message, errorCode);
    }

    /**
     * 생성자 - 메시지, 에러 코드, 원인 예외
     * @param message 에러 메시지
     * @param errorCode 에러 코드
     * @param cause 원인 예외
     */
    protected ParsingException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
