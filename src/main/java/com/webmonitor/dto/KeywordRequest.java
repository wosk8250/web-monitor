package com.webmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 키워드 등록/수정 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordRequest {

    private String keyword; // 감시할 키워드 (빈 문자열이면 새글 감지 활성화)

    private Long siteId; // 사이트 ID (null이면 전체 공통 키워드)

    private Boolean active = true; // 활성화 여부 (기본값: true)
}
