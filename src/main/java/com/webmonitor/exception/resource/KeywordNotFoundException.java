package com.webmonitor.exception.resource;

/**
 * 키워드를 찾을 수 없음 예외
 * Keyword ID로 조회 실패 시 발생
 */
public class KeywordNotFoundException extends ResourceNotFoundException {

    private static final String ERROR_CODE = "RESOURCE_002";

    /**
     * 생성자 - Keyword ID
     * @param keywordId 키워드 ID
     */
    public KeywordNotFoundException(Long keywordId) {
        super(String.format("키워드를 찾을 수 없습니다. (ID: %d)", keywordId), ERROR_CODE);
        addContext("keywordId", keywordId);
    }

    /**
     * 생성자 - 커스텀 메시지
     * @param message 에러 메시지
     */
    public KeywordNotFoundException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - Keyword ID와 원인 예외
     * @param keywordId 키워드 ID
     * @param cause 원인 예외
     */
    public KeywordNotFoundException(Long keywordId, Throwable cause) {
        super(String.format("키워드를 찾을 수 없습니다. (ID: %d)", keywordId), ERROR_CODE, cause);
        addContext("keywordId", keywordId);
    }
}
