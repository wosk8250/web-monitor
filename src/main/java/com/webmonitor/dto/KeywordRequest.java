package com.webmonitor.dto;

import com.webmonitor.util.XssSanitizer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 키워드 등록/수정 요청 DTO
 * XSS 방어: keyword 필드는 setter에서 자동으로 HTML 이스케이프 처리
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordRequest {

    @NotNull(message = "키워드는 필수입니다")
    @Size(max = 200, message = "키워드는 최대 200자까지 입력 가능합니다")
    private String keyword; // 감시할 키워드 (빈 문자열이면 새글 감지 활성화)

    private Long siteId; // 사이트 ID (null이면 전체 공통 키워드)

    private Boolean active = true; // 활성화 여부 (기본값: true)

    /**
     * XSS 방어: keyword setter 오버라이드
     * HTML 태그를 이스케이프 처리하여 XSS 공격 방지
     */
    public void setKeyword(String keyword) {
        this.keyword = XssSanitizer.sanitize(keyword);
    }
}
