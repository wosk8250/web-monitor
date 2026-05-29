package com.webmonitor.exception.parsing;

/**
 * 게시글 추출 실패 예외
 * articleSelector가 잘못되었거나, 페이지 구조 변경 등으로 게시글 추출 실패 시 발생
 */
public class ArticleExtractionException extends ParsingException {

    private static final String ERROR_CODE = "PARSING_001";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public ArticleExtractionException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public ArticleExtractionException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * 사이트와 셀렉터 정보를 컨텍스트에 추가하는 편의 메서드
     * @param siteId 사이트 ID
     * @param articleSelector 게시글 셀렉터
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public ArticleExtractionException withSiteInfo(Long siteId, String articleSelector) {
        addContext("siteId", siteId);
        addContext("articleSelector", articleSelector);
        return this;
    }
}
