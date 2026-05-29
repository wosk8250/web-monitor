package com.webmonitor.exception.resource;

/**
 * 제품을 찾을 수 없음 예외
 * Product ID로 조회 실패 시 발생
 */
public class ProductNotFoundException extends ResourceNotFoundException {

    private static final String ERROR_CODE = "RESOURCE_004";

    /**
     * 생성자 - Product ID
     * @param productId 제품 ID
     */
    public ProductNotFoundException(Long productId) {
        super(String.format("제품을 찾을 수 없습니다. (ID: %d)", productId), ERROR_CODE);
        addContext("productId", productId);
    }

    /**
     * 생성자 - 커스텀 메시지
     * @param message 에러 메시지
     */
    public ProductNotFoundException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - Product ID와 원인 예외
     * @param productId 제품 ID
     * @param cause 원인 예외
     */
    public ProductNotFoundException(Long productId, Throwable cause) {
        super(String.format("제품을 찾을 수 없습니다. (ID: %d)", productId), ERROR_CODE, cause);
        addContext("productId", productId);
    }
}
