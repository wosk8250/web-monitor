package com.webmonitor.dto;

import com.webmonitor.domain.Keyword;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 키워드 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordResponse {

    private Long id; // 키워드 ID
    private String keyword; // 감시할 키워드
    private Long siteId; // 사이트 ID (null이면 전체 공통 키워드)
    private String siteName; // 사이트 이름
    private Boolean active; // 활성화 여부
    private LocalDateTime createdAt; // 생성 시간

    /**
     * Keyword 엔티티를 KeywordResponse DTO로 변환
     * @param keyword 키워드 엔티티
     * @return KeywordResponse DTO
     */
    public static KeywordResponse from(Keyword keyword) {
        return KeywordResponse.builder()
                .id(keyword.getId())
                .keyword(keyword.getKeyword())
                .siteId(keyword.getSite() != null ? keyword.getSite().getId() : null)
                .siteName(keyword.getSite() != null ? keyword.getSite().getName() : "전체 공통")
                .active(keyword.getActive())
                .createdAt(keyword.getCreatedAt())
                .build();
    }
}
