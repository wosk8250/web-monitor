package com.webmonitor.domain;

import com.webmonitor.validation.ValidationMessages;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 제품 재고 모니터링 엔티티
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_active", columnList = "active"),
    @Index(name = "idx_product_priority", columnList = "priority"),
    @Index(name = "idx_product_active_priority", columnList = "active, priority"),
    @Index(name = "idx_product_discord_user", columnList = "discordUserId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = ValidationMessages.PRODUCT_NAME_REQUIRED)
    @Size(min = 1, max = 500, message = ValidationMessages.PRODUCT_NAME_LENGTH)
    @Column(nullable = false, length = 500)
    private String name;  // 상품명

    @NotBlank(message = ValidationMessages.PRODUCT_URL_REQUIRED)
    @Size(min = 10, max = 1000, message = ValidationMessages.PRODUCT_URL_LENGTH)
    @Column(nullable = false, length = 1000)
    private String url;   // 상품 URL

    @Size(max = 100, message = ValidationMessages.PRODUCT_SHOP_NAME_LENGTH)
    @Column(length = 100)
    private String shopName;  // 쇼핑몰 이름 (예: 쿠팡, 11번가)

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private StockStatus currentStatus = StockStatus.UNKNOWN;  // 현재 재고 상태

    @Column
    @Enumerated(EnumType.STRING)
    private StockStatus previousStatus;  // 이전 재고 상태

    @DecimalMin(value = "0.00", message = ValidationMessages.PRODUCT_PRICE_MIN)
    @Column(precision = 10, scale = 2)
    private BigDecimal currentPrice;  // 현재 가격

    @DecimalMin(value = "0.00", message = ValidationMessages.PRODUCT_PRICE_MIN)
    @Column(precision = 10, scale = 2)
    private BigDecimal previousPrice;  // 이전 가격

    @Size(max = 1000, message = ValidationMessages.PRODUCT_IMAGE_URL_LENGTH)
    @Column(length = 1000)
    private String imageUrl;  // 상품 이미지 URL

    @Size(max = 500, message = ValidationMessages.PRODUCT_CONTENT_SELECTOR_LENGTH)
    @Column(length = 500)
    private String contentSelector;  // CSS 셀렉터 (전체 페이지 모니터링 시 특정 요소 지정, null이면 전체 페이지)

    @Size(max = 64)
    @Column(length = 64)
    private String lastContentHash;  // 마지막 콘텐츠 해시값 (파서 없이 해시 비교 방식 사용 시)

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;  // 모니터링 활성화

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyOnRestock = true;  // 재입고 알림 설정

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Priority priority = Priority.NORMAL;  // 우선순위 (긴급/일반)

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Min(value = 1, message = ValidationMessages.PRODUCT_CHECK_INTERVAL_MIN)
    @Max(value = 1440, message = ValidationMessages.PRODUCT_CHECK_INTERVAL_MAX)
    @Column(nullable = false)
    @Builder.Default
    private Integer checkIntervalMinutes = 3;  // 개별 체크 주기 (기본 3분)

    @Min(value = 20, message = ValidationMessages.PRODUCT_CHECK_INTERVAL_SECONDS_MIN)
    @Max(value = 3600, message = ValidationMessages.PRODUCT_CHECK_INTERVAL_SECONDS_MAX)
    @Column
    private Integer checkIntervalSeconds;  // 초 단위 체크 주기 (선택사항, null이면 checkIntervalMinutes 사용)

    private LocalDateTime lastCheckedAt;  // 마지막 체크 시간

    private LocalDateTime lastRestockAlertAt;  // 마지막 재입고 알림 시간

    private LocalDateTime lastContentChangeAlertAt;  // 마지막 콘텐츠 변경 알림 시간

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false)
    @Builder.Default
    private Boolean notifyOnContentChange = true;  // 콘텐츠 변경 알림 설정

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Min(value = 0, message = ValidationMessages.PRODUCT_CONSECUTIVE_FAILURES_MIN)
    @Column(nullable = false)
    @Builder.Default
    private Integer consecutiveFailures = 0;  // 연속 실패 횟수

    private LocalDateTime lastFailureAt;  // 마지막 실패 시간

    @Size(max = 20, message = ValidationMessages.PRODUCT_DISCORD_USER_ID_LENGTH)
    @Column(length = 20)  // Discord User ID (Snowflake, 18-19자리)
    private String discordUserId;  // 디스코드 사용자 ID (제품 소유자)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 재고 상태 enum
     */
    public enum StockStatus {
        IN_STOCK,      // 재고 있음
        OUT_OF_STOCK,  // 품절
        UNKNOWN        // 알 수 없음
    }

    /**
     * 우선순위 enum
     * URGENT: 10초 스케줄러에서 체크
     * NORMAL: 60초 스케줄러에서 체크
     */
    public enum Priority {
        URGENT,   // 긴급 (초인기 제품)
        NORMAL    // 일반 (일반 제품)
    }
}
