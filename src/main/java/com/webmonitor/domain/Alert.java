package com.webmonitor.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.webmonitor.validation.ValidationMessages;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 키워드 감지 시 생성되는 알림 기록을 저장하는 엔티티
 */
@Entity // JPA 엔티티로 지정 (데이터베이스 테이블과 매핑)
@Table(name = "alerts", indexes = {
    @Index(name = "idx_alert_site", columnList = "site_id"),
    @Index(name = "idx_alert_keyword", columnList = "keyword_id"),
    @Index(name = "idx_alert_product", columnList = "product_id"),
    @Index(name = "idx_alert_detected_at", columnList = "detected_at"),
    @Index(name = "idx_alert_site_detected",  columnList = "site_id, detected_at"),
    @Index(name = "idx_alert_sent_priority", columnList = "sent, priority, detected_at")
}) // 테이블 이름 및 인덱스 설정
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class Alert {

    @Id // 기본 키(Primary Key) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략 (DB에서 자동으로 ID 생성)
    private Long id;

    @Version // 낙관적 락(Optimistic Locking)을 위한 버전 필드
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩 (필요할 때만 Site 정보를 조회)
    @JoinColumn(name = "site_id", nullable = true) // 외래 키 컬럼명을 'site_id'로 지정 (제품 알림 시 null 가능)
    @JsonIgnore // JSON 직렬화 시 무한 순환 참조 방지
    private Site site; // 알림이 발생한 사이트 (제품 재입고 알림의 경우 null)

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩 (필요할 때만 Keyword 정보를 조회)
    @JoinColumn(name = "keyword_id", nullable = true) // 외래 키 컬럼명을 'keyword_id'로 지정 (전체 페이지 변경 감지 시 null 가능)
    @JsonIgnore // JSON 직렬화 시 무한 순환 참조 방지
    private Keyword keyword; // 감지된 키워드 (전체 페이지 변경 감지 알림의 경우 null일 수 있음)

    @ManyToOne(fetch = FetchType.LAZY) // 다대일 관계, 지연 로딩 (필요할 때만 Product 정보를 조회)
    @JoinColumn(name = "product_id", nullable = true) // 외래 키 컬럼명을 'product_id'로 지정 (재고 알림 시에만 사용)
    @JsonIgnore // JSON 직렬화 시 무한 순환 참조 방지
    private Product product; // 재입고가 감지된 제품 (제품 재입고 알림의 경우에만 값 존재)

    @NotNull(message = ValidationMessages.ALERT_TYPE_REQUIRED)
    @Column(nullable = false)
    @Enumerated(EnumType.STRING) // enum을 문자열로 저장
    @Builder.Default
    private AlertType alertType = AlertType.KEYWORD; // 알림 타입 (기본값: 키워드)

    @NotBlank(message = ValidationMessages.ALERT_MESSAGE_REQUIRED)
    @Size(min = 1, max = 10000, message = ValidationMessages.ALERT_MESSAGE_LENGTH)
    @Column(nullable = false, columnDefinition = "TEXT") // null 불가, TEXT 타입으로 지정 (긴 텍스트 저장 가능)
    private String message; // 알림 메시지 내용

    @Size(max = 500, message = ValidationMessages.ALERT_PAGE_TITLE_LENGTH)
    @Column(length = 500) // 최대 길이 500자
    private String pageTitle; // 페이지 제목

    @NotBlank(message = ValidationMessages.ALERT_DETECTED_URL_REQUIRED)
    @Size(min = 10, max = 500, message = ValidationMessages.ALERT_DETECTED_URL_LENGTH)
    @Column(nullable = false, length = 500) // null 불가, 최대 길이 500자
    private String detectedUrl; // 키워드가 감지된 URL

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시간 저장
    @Column(nullable = false, updatable = false) // null 불가, 수정 불가
    private LocalDateTime detectedAt; // 키워드 감지 시간

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false) // null 불가
    @Builder.Default
    private Boolean sent = false; // 알림 전송 여부 (기본값: false)

    private LocalDateTime sentAt; // 알림 전송 시간 (전송 전에는 null)

    @NotNull(message = ValidationMessages.ALERT_PRIORITY_REQUIRED)
    @Column(nullable = false) // null 불가
    @Enumerated(EnumType.STRING) // enum을 문자열로 저장
    @Builder.Default
    private Priority priority = Priority.NORMAL; // 우선순위 (기본값: NORMAL)

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Min(value = 0, message = ValidationMessages.ALERT_RETRY_COUNT_MIN)
    @Max(value = 10, message = ValidationMessages.ALERT_RETRY_COUNT_MAX)
    @Column(nullable = false) // null 불가
    @Builder.Default
    private Integer retryCount = 0; // 재시도 횟수 (기본값: 0)

    @Size(max = 500, message = ValidationMessages.ALERT_ERROR_MESSAGE_LENGTH)
    @Column(length = 500) // 최대 길이 500자
    private String lastErrorMessage; // 마지막 발송 실패 에러 메시지

    // 최대 재시도 횟수 (3회)
    public static final int MAX_RETRIES = 3;

    // alertType은 생성 후 불변 — @PrePersist에서만 설정, UPDATE 시 재계산 불필요
    @PrePersist
    public void syncPriority() {
        this.priority = (alertType == AlertType.PRODUCT_RESTOCK) ? Priority.HIGH : Priority.NORMAL;
    }

    /**
     * 재시도 가능 여부 확인
     * @return 재시도 가능하면 true
     */
    public boolean canRetry() {
        return retryCount < MAX_RETRIES;
    }

    /**
     * 재시도 횟수 증가
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /**
     * 알림 타입 enum
     */
    public enum AlertType {
        KEYWORD,           // 키워드 감지 알림
        CONTENT_CHANGE,    // 내용 변경 알림 (전체 페이지)
        PRODUCT_RESTOCK    // 제품 재입고 알림
    }

    /**
     * 우선순위 enum
     * HIGH: 재고 알림 (즉시 발송)
     * NORMAL: 새글 알림 (대기열에서 천천히 발송)
     */
    public enum Priority {
        HIGH,     // 높음 (재고 알림) - 즉시 발송
        NORMAL    // 보통 (새글 알림) - 대기열 처리
    }
}
