package com.webmonitor.dto;

import com.webmonitor.domain.Alert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 조회 응답 DTO
 * 클라이언트에게 알림 정보를 반환할 때 사용
 */
@Data // @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class AlertResponse {

    /**
     * 알림 ID
     */
    private Long id;

    /**
     * 사이트 ID
     */
    private Long siteId;

    /**
     * 사이트 이름
     */
    private String siteName;

    /**
     * 사이트 URL
     */
    private String siteUrl;

    /**
     * 키워드 ID
     */
    private Long keywordId;

    /**
     * 감지된 키워드 텍스트
     */
    private String keywordText;

    /**
     * 알림 메시지
     */
    private String message;

    /**
     * 페이지 제목
     */
    private String pageTitle;

    /**
     * 키워드가 감지된 URL
     */
    private String detectedUrl;

    /**
     * 키워드 감지 시간
     */
    private LocalDateTime detectedAt;

    /**
     * 알림 전송 여부
     */
    private Boolean sent;

    /**
     * 알림 전송 시간
     */
    private LocalDateTime sentAt;

    /**
     * Entity를 DTO로 변환하는 정적 팩토리 메서드
     * @param alert Alert 엔티티
     * @return AlertResponse DTO
     */
    public static AlertResponse from(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .siteId(alert.getSite().getId())
                .siteName(alert.getSite().getName())
                .siteUrl(alert.getSite().getUrl())
                .keywordId(alert.getKeyword() != null ? alert.getKeyword().getId() : null)
                .keywordText(alert.getKeyword() != null ? alert.getKeyword().getKeyword() : null)
                .message(alert.getMessage())
                .pageTitle(alert.getPageTitle())
                .detectedUrl(alert.getDetectedUrl())
                .detectedAt(alert.getDetectedAt())
                .sent(alert.getSent())
                .sentAt(alert.getSentAt())
                .build();
    }
}
