package com.webmonitor.dto;

import com.webmonitor.domain.Site;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사이트 조회 응답 DTO
 * 클라이언트에게 사이트 정보를 반환할 때 사용
 */
@Data // @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class SiteResponse {

    /**
     * 사이트 ID
     */
    private Long id;

    /**
     * 사이트 이름
     */
    private String name;

    /**
     * 사이트 URL
     */
    private String url;

    /**
     * 체크 주기 (분 단위)
     */
    private Integer checkInterval;

    /**
     * 활성화 여부
     */
    private Boolean active;

    /**
     * 생성 시간
     */
    private LocalDateTime createdAt;

    /**
     * 수정 시간
     */
    private LocalDateTime updatedAt;

    /**
     * 등록된 키워드 개수
     */
    private Integer keywordCount;

    /**
     * Entity를 DTO로 변환하는 정적 팩토리 메서드
     * @param site Site 엔티티
     * @return SiteResponse DTO
     */
    public static SiteResponse from(Site site) {
        return SiteResponse.builder()
                .id(site.getId())
                .name(site.getName())
                .url(site.getUrl())
                .checkInterval(site.getCheckInterval())
                .active(site.getActive())
                .createdAt(site.getCreatedAt())
                .updatedAt(site.getUpdatedAt())
                .keywordCount(site.getKeywords() != null ? site.getKeywords().size() : 0)
                .build();
    }
}
