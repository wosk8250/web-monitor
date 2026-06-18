package com.webmonitor.validation;

/**
 * Bean Validation 메시지 상수 클래스
 * 일관된 검증 메시지 제공
 */
public class ValidationMessages {

    // Site 검증 메시지
    public static final String SITE_NAME_REQUIRED = "사이트 이름은 필수입니다";
    public static final String SITE_NAME_LENGTH = "사이트 이름은 1-200자여야 합니다";
    public static final String SITE_URL_REQUIRED = "URL은 필수입니다";
    public static final String SITE_URL_PATTERN = "유효한 HTTP(S) URL을 입력하세요";
    public static final String SITE_URL_LENGTH = "URL은 10-500자여야 합니다";
    public static final String SITE_CHECK_INTERVAL_MIN = "체크 주기는 최소 1분이어야 합니다";
    public static final String SITE_CHECK_INTERVAL_MAX = "체크 주기는 24시간(1440분)을 초과할 수 없습니다";
    public static final String SITE_ARTICLE_SELECTOR_LENGTH = "게시글 셀렉터는 최대 500자까지 입력 가능합니다";
    public static final String SITE_DISCORD_USER_ID_LENGTH = "Discord User ID는 최대 20자까지 입력 가능합니다";

    // Alert 검증 메시지
    public static final String ALERT_MESSAGE_REQUIRED = "알림 메시지는 필수입니다";
    public static final String ALERT_MESSAGE_LENGTH = "알림 메시지는 1-10000자여야 합니다";
    public static final String ALERT_DETECTED_URL_REQUIRED = "감지 URL은 필수입니다";
    public static final String ALERT_DETECTED_URL_LENGTH = "감지 URL은 10-500자여야 합니다";
    public static final String ALERT_PAGE_TITLE_LENGTH = "페이지 제목은 최대 500자까지 입력 가능합니다";
    public static final String ALERT_ERROR_MESSAGE_LENGTH = "에러 메시지는 최대 500자까지 입력 가능합니다";
    public static final String ALERT_TYPE_REQUIRED = "알림 타입은 필수입니다";
    public static final String ALERT_PRIORITY_REQUIRED = "우선순위는 필수입니다";
    public static final String ALERT_RETRY_COUNT_MIN = "재시도 횟수는 0 이상이어야 합니다";
    public static final String ALERT_RETRY_COUNT_MAX = "재시도 횟수는 10을 초과할 수 없습니다";

    // Product 검증 메시지
    public static final String PRODUCT_NAME_REQUIRED = "상품명은 필수입니다";
    public static final String PRODUCT_NAME_LENGTH = "상품명은 1-500자여야 합니다";
    public static final String PRODUCT_URL_REQUIRED = "상품 URL은 필수입니다";
    public static final String PRODUCT_URL_LENGTH = "상품 URL은 10-1000자여야 합니다";
    public static final String PRODUCT_SHOP_NAME_LENGTH = "쇼핑몰 이름은 최대 100자까지 입력 가능합니다";
    public static final String PRODUCT_IMAGE_URL_LENGTH = "이미지 URL은 최대 1000자까지 입력 가능합니다";
    public static final String PRODUCT_CONTENT_SELECTOR_LENGTH = "콘텐츠 셀렉터는 최대 500자까지 입력 가능합니다";
    public static final String PRODUCT_CHECK_INTERVAL_MIN = "체크 주기는 최소 1분이어야 합니다";
    public static final String PRODUCT_CHECK_INTERVAL_MAX = "체크 주기는 1440분(24시간)을 초과할 수 없습니다";
    public static final String PRODUCT_CHECK_INTERVAL_SECONDS_MIN = "초 단위 체크 주기는 최소 20초이어야 합니다";
    public static final String PRODUCT_CHECK_INTERVAL_SECONDS_MAX = "초 단위 체크 주기는 최대 3600초(1시간)을 초과할 수 없습니다";
    public static final String PRODUCT_DISCORD_USER_ID_LENGTH = "Discord User ID는 최대 20자까지 입력 가능합니다";
    public static final String PRODUCT_PRICE_MIN = "가격은 0 이상이어야 합니다";
    public static final String PRODUCT_CONSECUTIVE_FAILURES_MIN = "연속 실패 횟수는 0 이상이어야 합니다";

    // Keyword 검증 메시지
    public static final String KEYWORD_REQUIRED = "키워드는 필수입니다";
    public static final String KEYWORD_LENGTH = "키워드는 1-100자여야 합니다";

    // Setting 검증 메시지
    public static final String SETTING_WEBHOOK_URL_LENGTH = "Webhook URL은 최대 500자까지 입력 가능합니다";

    // 공통 검증 메시지
    public static final String COMMON_NOT_NULL = "필수 항목입니다";
    public static final String COMMON_NOT_BLANK = "빈 값을 입력할 수 없습니다";

    // 생성자 private으로 인스턴스 생성 방지
    private ValidationMessages() {
        throw new AssertionError("Utility class should not be instantiated");
    }
}
