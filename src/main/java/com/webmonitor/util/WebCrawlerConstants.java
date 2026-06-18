package com.webmonitor.util;

/**
 * 웹 크롤러 관련 상수 정의
 * User-Agent, 타임아웃, 재시도 설정 등 크롤링에 필요한 모든 상수를 중앙화
 */
public final class WebCrawlerConstants {

    // Private constructor to prevent instantiation
    private WebCrawlerConstants() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * User-Agent 문자열 배열
     * 다양한 브라우저와 운영체제를 에뮬레이션하여 크롤링 차단 방지
     */
    public static final String[] USER_AGENTS = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.1; rv:121.0) Gecko/20100101 Firefox/121.0"
    };

    // ==================== 타임아웃 설정 ====================

    /**
     * 짧은 타임아웃 (robots.txt 등 가벼운 요청용)
     */
    public static final int TIMEOUT_SHORT_MS = 3000;  // 3초

    /**
     * 기본 타임아웃 (일반 웹페이지 크롤링용)
     */
    public static final int TIMEOUT_DEFAULT_MS = 5000;  // 5초

    /**
     * 긴 타임아웃 (무거운 페이지 또는 느린 서버용)
     */
    public static final int TIMEOUT_LONG_MS = 10000;  // 10초

    // ==================== 재시도 설정 ====================

    /**
     * 최대 재시도 횟수
     */
    public static final int MAX_RETRY_ATTEMPTS = 3;

    /**
     * 초기 재시도 대기 시간 (밀리초)
     * exponential backoff의 기준값
     */
    public static final long INITIAL_RETRY_DELAY_MS = 1000;  // 1초

    // ==================== 네트워크 포트 ====================

    /**
     * HTTP 기본 포트
     */
    public static final int HTTP_PORT = 80;

    /**
     * HTTPS 기본 포트
     */
    public static final int HTTPS_PORT = 443;

    // ==================== 캐시 및 리소스 제한 ====================

    /**
     * Rate Limiting 캐시 최대 크기
     * (도메인별 마지막 요청 시간 추적용)
     */
    public static final int RATE_LIMIT_CACHE_SIZE = 1000;

    /**
     * Rate Limiting 기본 대기 시간 (밀리초)
     */
    public static final long DEFAULT_RATE_LIMIT_MS = 1000;  // 1초

    /**
     * 응답 본문 최대 크기 (바이트) — 웹 모니터링용
     * 무제한 허용 시 대용량 페이지로 인한 OOM 방지
     */
    public static final int MAX_BODY_SIZE_BYTES = 5 * 1024 * 1024;  // 5MB

    /**
     * 응답 본문 최대 크기 (바이트) — 제품 페이지 모니터링용
     * 제품 페이지는 상대적으로 가벼우므로 더 엄격한 제한 적용
     */
    public static final int PRODUCT_MAX_BODY_SIZE_BYTES = 1024 * 1024;  // 1MB
}
