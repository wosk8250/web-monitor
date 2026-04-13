package com.webmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사이트 등록/수정 요청 DTO
 * 클라이언트로부터 사이트 정보를 받을 때 사용
 */
@Data // @Getter, @Setter, @ToString, @EqualsAndHashCode, @RequiredArgsConstructor 자동 생성
@NoArgsConstructor // 파라미터가 없는 기본 생성자 자동 생성
@AllArgsConstructor // 모든 필드를 파라미터로 받는 생성자 자동 생성
@Builder // 빌더 패턴 사용 가능하게 함
public class SiteRequest {

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
     * 활성화 여부 (기본값: true)
     */
    private Boolean active;
}
