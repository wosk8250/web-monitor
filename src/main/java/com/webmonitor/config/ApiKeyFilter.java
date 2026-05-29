package com.webmonitor.config;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API 키 인증 필터
 * /api/** 경로에 대한 API 키 검증
 * BCrypt 해싱을 사용하여 보안 강화
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api.security.enabled", havingValue = "true", matchIfMissing = false)
public class ApiKeyFilter extends OncePerRequestFilter {

    @Value("${api.security.key:}")
    private String validApiKeyHash;  // BCrypt 해시값 저장

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String API_PATH_PREFIX = "/api/";

    // BCrypt 인코더 (strength=10, 보안과 성능 균형)
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 애플리케이션 시작 시 API 키 설정 검증
     * BCrypt 해시 형식이 올바른지 확인
     */
    @PostConstruct
    public void validateConfiguration() {
        if (validApiKeyHash == null || validApiKeyHash.trim().isEmpty()) {
            log.error("API Key 해시가 설정되지 않았습니다. api.security.key를 설정하세요.");
        } else if (!validApiKeyHash.startsWith("$2a$") && !validApiKeyHash.startsWith("$2b$")) {
            log.warn("API Key가 BCrypt 해시 형식이 아닙니다. BCrypt로 인코딩된 값을 사용하세요.");
        } else {
            log.info("API Key 설정 검증 완료 (BCrypt 해시 형식)");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestPath = request.getRequestURI();

        // /api/** 경로에만 API 키 검증 적용
        if (requestPath.startsWith(API_PATH_PREFIX)) {
            String providedApiKey = request.getHeader(API_KEY_HEADER);

            // API 키 해시가 설정되지 않은 경우 경고
            if (validApiKeyHash == null || validApiKeyHash.trim().isEmpty()) {
                log.warn("API 보안이 활성화되었으나 API 키 해시가 설정되지 않았습니다.");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"API security not configured\"}");
                return;
            }

            // API 키 검증 (BCrypt 해싱)
            if (providedApiKey == null || !passwordEncoder.matches(providedApiKey, validApiKeyHash)) {
                log.warn("유효하지 않은 API 키로 요청 시도: {} (IP: {})",
                        sanitizePathForLogging(requestPath), request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Invalid or missing API key\"}");
                return;
            }

            log.debug("API 키 인증 성공: {}", sanitizePathForLogging(requestPath));
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 로깅용 경로 정제 (쿼리 스트링 제거하여 민감 정보 노출 방지)
     * @param path 원본 경로
     * @return 쿼리 스트링이 제거된 경로
     */
    private String sanitizePathForLogging(String path) {
        if (path == null) {
            return null;
        }
        int queryIndex = path.indexOf('?');
        return queryIndex == -1 ? path : path.substring(0, queryIndex);
    }
}
