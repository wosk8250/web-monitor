package com.webmonitor.dto;

import com.webmonitor.util.XssSanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

/**
 * 사이트 등록/수정 요청 DTO
 * 클라이언트로부터 사이트 정보를 받을 때 사용
 * XSS 방어: name, url 필드는 setter에서 자동으로 HTML 이스케이프 처리
 */
@Data // @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class SiteRequest {

    /**
     * 사이트 이름
     */
    @NotBlank(message = "사이트 이름은 필수입니다")
    @Size(max = 200, message = "사이트 이름은 최대 200자까지 입력 가능합니다")
    private String name;

    /**
     * 사이트 URL
     */
    @NotBlank(message = "사이트 URL은 필수입니다")
    @URL(message = "올바른 URL 형식이 아닙니다")
    @Size(max = 1000, message = "URL은 최대 1000자까지 입력 가능합니다")
    private String url;

    /**
     * 활성화 여부 (기본값: true)
     */
    private Boolean active;

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
}
