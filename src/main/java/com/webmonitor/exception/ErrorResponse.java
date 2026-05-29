package com.webmonitor.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 에러 응답 DTO
 * 모든 예외를 일관된 형식으로 반환
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /**
     * 에러 발생 시간
     */
    private LocalDateTime timestamp;

    /**
     * HTTP 상태 코드
     */
    private int status;

    /**
     * 에러 유형
     */
    private String error;

    /**
     * 에러 메시지
     */
    private String message;

    /**
     * 상세 에러 정보 (필드별 검증 실패, 컨텍스트 정보 등)
     * String뿐만 아니라 Long, Integer, Boolean 등 다양한 타입을 저장 가능
     */
    private Map<String, Object> details;
}
