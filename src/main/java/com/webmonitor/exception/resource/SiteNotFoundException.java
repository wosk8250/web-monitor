package com.webmonitor.exception.resource;

/**
 * 사이트를 찾을 수 없음 예외
 * Site ID로 조회 실패 시 발생
 */
public class SiteNotFoundException extends ResourceNotFoundException {

    private static final String ERROR_CODE = "RESOURCE_001";

    /**
     * 생성자 - Site ID
     * @param siteId 사이트 ID
     */
    public SiteNotFoundException(Long siteId) {
        super(String.format("사이트를 찾을 수 없습니다. (ID: %d)", siteId), ERROR_CODE);
        addContext("siteId", siteId);
    }

    /**
     * 생성자 - 커스텀 메시지
     * @param message 에러 메시지
     */
    public SiteNotFoundException(String message) {
        super(message, ERROR_CODE);
    }

    /**
     * 생성자 - Site ID와 원인 예외
     * @param siteId 사이트 ID
     * @param cause 원인 예외
     */
    public SiteNotFoundException(Long siteId, Throwable cause) {
        super(String.format("사이트를 찾을 수 없습니다. (ID: %d)", siteId), ERROR_CODE, cause);
        addContext("siteId", siteId);
    }
}
