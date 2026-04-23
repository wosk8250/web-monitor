package com.webmonitor.dto;

import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "키워드는 필수입니다")
    private String keyword; // 감시할 키워드

    private Long siteId; // 사이트 ID (null이면 전체 공통 키워드)

    private Boolean active = true; // 활성화 여부 (기본값: true)
}
