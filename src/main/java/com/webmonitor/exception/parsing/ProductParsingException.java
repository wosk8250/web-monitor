package com.webmonitor.exception.parsing;

/**
 * 제품 정보 파싱 실패 예외
 * 제품 재고 상태, 가격, 이름 등 파싱 실패 시 발생
 */
public class ProductParsingException extends ParsingException {

    private static final String ERROR_CODE = "PARSING_002";

    /**
     * 생성자 - 메시지만
     * @param message 에러 메시지
     */
    public ProductParsingException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - 메시지와 원인 예외
     * @param message 에러 메시지
     * @param cause 원인 예외
     */
    public ProductParsingException(String message, Throwable cause) {
        super(message, ERROR_CODE, cause);
    }

    /**
     * 제품과 파서 정보를 컨텍스트에 추가하는 편의 메서드
     * @param productId 제품 ID
     * @param parserType 파서 타입 (예: "NAVER_STORE", "COUPANG")
     * @return 현재 예외 객체 (메서드 체이닝)
     */
    public ProductParsingException withProductInfo(Long productId, String parserType) {
        addContext("productId", productId);
        addContext("parserType", parserType);
        return this;
    }
}
