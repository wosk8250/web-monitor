package com.webmonitor.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.webmonitor.util.XssSanitizer;
import com.webmonitor.validation.ValidationMessages;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 모니터링할 사이트 정보를 저장하는 엔티티
 * XSS 방어: name, url, articleSelector, discordUserId 필드는 setter에서 자동으로 HTML 이스케이프 처리
 */
@Entity // JPA 엔티티로 지정 (데이터베이스 테이블과 매핑)
@Table(name = "sites", indexes = {
        @Index(name = "idx_site_discord_user", columnList = "discordUserId")
}) // 테이블 이름을 'sites'로 지정, Discord User ID 인덱스 추가
@Getter // 모든 필드의 Getter 메서드 자동 생성
@Setter // 모든 필드의 Setter 메서드 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class Site {

    @Id // 기본 키(Primary Key) 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략 (DB에서 자동으로 ID 생성)
    private Long id;

    @NotBlank(message = ValidationMessages.SITE_NAME_REQUIRED)
    @Size(min = 1, max = 200, message = ValidationMessages.SITE_NAME_LENGTH)
    @Column(nullable = false, length = 200) // null 불가, 최대 길이 200자
    private String name; // 사이트 이름

    @NotBlank(message = ValidationMessages.SITE_URL_REQUIRED)
    @Pattern(regexp = "^https?://.*", message = ValidationMessages.SITE_URL_PATTERN)
    @Size(min = 10, max = 500, message = ValidationMessages.SITE_URL_LENGTH)
    @Column(nullable = false, length = 500) // null 불가, 최대 길이 500자
    private String url; // 사이트 URL

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false) // null 불가
    @Builder.Default
    private Boolean active = true; // 활성화 여부 (기본값: true)

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Column(nullable = false) // null 불가
    @Builder.Default
    private Boolean detectContentChange = false; // 전체 페이지 변경 감지 여부 (기본값: false)

    @Size(max = 64)
    @Column(length = 64) // SHA-256 해시값 저장 (64자)
    private String lastContentHash; // 마지막 크롤링한 콘텐츠의 해시값 (중복 감지용)

    @Size(max = 500, message = ValidationMessages.SITE_ARTICLE_SELECTOR_LENGTH)
    @Column(length = 500)
    private String articleSelector; // 게시글 링크를 찾기 위한 CSS 셀렉터 (예: "a.article-title", ".post-link")

    @NotNull(message = ValidationMessages.COMMON_NOT_NULL)
    @Min(value = 1, message = ValidationMessages.SITE_CHECK_INTERVAL_MIN)
    @Max(value = 1440, message = ValidationMessages.SITE_CHECK_INTERVAL_MAX)
    @Column(nullable = false)
    @Builder.Default
    private Integer checkIntervalMinutes = 1; // 개별 체크 주기 (기본값: 1분)

    private LocalDateTime lastCheckedAt; // 마지막 체크 시간

    @Size(max = 20, message = ValidationMessages.SITE_DISCORD_USER_ID_LENGTH)
    @Column(length = 20) // Discord User ID (Snowflake, 18-19자리)
    private String discordUserId; // 디스코드 사용자 ID (사이트 소유자)

    @CreationTimestamp // 엔티티 생성 시 자동으로 현재 시간 저장
    @Column(nullable = false, updatable = false) // null 불가, 수정 불가
    private LocalDateTime createdAt; // 생성 시간

    @UpdateTimestamp // 엔티티 수정 시 자동으로 현재 시간 저장
    @Column(nullable = false) // null 불가
    private LocalDateTime updatedAt; // 수정 시간

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true) // 일대다 관계, Keyword의 site 필드와 매핑, 연쇄 작업, 고아 객체 제거
    @Builder.Default // 빌더 패턴 사용 시 기본값 지정
    @JsonIgnore // JSON 직렬화 시 무한 순환 참조 방지
    private List<Keyword> keywords = new ArrayList<>(); // 해당 사이트에 등록된 키워드 목록

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL, orphanRemoval = true) // 일대다 관계, Alert의 site 필드와 매핑, 연쇄 작업, 고아 객체 제거
    @Builder.Default // 빌더 패턴 사용 시 기본값 지정
    @JsonIgnore // JSON 직렬화 시 무한 순환 참조 방지
    private List<Alert> alerts = new ArrayList<>(); // 해당 사이트에서 발생한 알림 목록

    /**
     * XSS 방어: name setter 오버라이드
     * HTML 태그를 이스케이프 처리하여 XSS 공격 방지
     */
    public void setName(String name) {
        this.name = XssSanitizer.sanitize(name);
    }

    /**
     * XSS 방어: url setter 오버라이드
     * HTML 태그를 이스케이프 처리하여 XSS 공격 방지
     */
    public void setUrl(String url) {
        this.url = XssSanitizer.sanitize(url);
    }

    /**
     * XSS 방어: articleSelector setter 오버라이드
     * HTML 태그를 이스케이프 처리하여 XSS 공격 방지
     */
    public void setArticleSelector(String articleSelector) {
        this.articleSelector = XssSanitizer.sanitize(articleSelector);
    }

    /**
     * XSS 방어: discordUserId setter 오버라이드
     * HTML 태그를 이스케이프 처리하여 XSS 공격 방지
     */
    public void setDiscordUserId(String discordUserId) {
        this.discordUserId = XssSanitizer.sanitize(discordUserId);
    }
}
