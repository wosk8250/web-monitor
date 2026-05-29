package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Product;
import com.webmonitor.util.DiscordConstants;
import com.webmonitor.util.DiscordUtils;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 디스코드 웹훅을 통해 알림을 전송하는 서비스
 * RestTemplate Bean 주입: Connection Pooling을 통한 성능 최적화
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordService {

    private final RestTemplate restTemplate;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 재시도 설정
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1초
    private static final long RATE_LIMIT_WAIT_MS = 60000; // 429 응답 시 대기 시간 (60초)

    /**
     * 디스코드로 알림 전송
     * @param webhookUrl 디스코드 웹훅 URL
     * @param alert 전송할 알림 정보
     */
    public void sendAlert(String webhookUrl, Alert alert) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        // 웹훅 URL 형식 검증
        if (!DiscordUtils.isValidDiscordWebhookUrl(webhookUrl)) {
            log.warn("잘못된 디스코드 웹훅 URL 형식입니다. URL: {}...", DiscordUtils.maskWebhookUrl(webhookUrl));
            return;
        }

        log.info("디스코드 알림 전송 시작 - 알림 ID: {}, 메시지: {}", alert.getId(), alert.getMessage());

        try {
            // Discord Embed 형식으로 메시지 구성
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", alert.getMessage());
            embed.put("url", alert.getDetectedUrl());
            embed.put("color", DiscordConstants.EMBED_COLOR_BLUE);
            embed.put("timestamp", alert.getDetectedAt().toString());

            // 필드 추가
            Map<String, Object> field1 = new HashMap<>();
            field1.put("name", "사이트");
            field1.put("value", alert.getSite() != null ? alert.getSite().getName() : "제품");
            field1.put("inline", true);

            Map<String, Object> field2 = new HashMap<>();
            field2.put("name", "시간");
            field2.put("value", alert.getDetectedAt().format(TIME_FORMATTER));
            field2.put("inline", true);

            embed.put("fields", List.of(field1, field2));

            // Footer 추가
            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "웹 모니터링 시스템");
            embed.put("footer", footer);

            // 최종 요청 바디 구성
            Map<String, Object> body = new HashMap<>();
            body.put("embeds", List.of(embed));

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // 재시도 로직이 포함된 웹훅 POST 요청
            ResponseEntity<String> response = sendDiscordWebhookWithRetry(
                    webhookUrl, request, "알림 전송 (ID: " + alert.getId() + ")");

            log.info("디스코드 알림 전송 성공 - 알림 ID: {}, HTTP 상태: {}",
                    alert.getId(), response.getStatusCode().value());

        } catch (RuntimeException e) {
            log.error("디스코드 알림 전송 실패 - 알림 ID: {}, 메시지: {}, 에러: {}",
                    alert.getId(), alert.getMessage(), e.getMessage());
            throw e; // AlertService의 재시도 메커니즘에서 처리하도록 예외 전파
        } catch (Exception e) {
            log.error("디스코드 알림 전송 중 예상치 못한 오류 - 알림 ID: {}, 에러: {}",
                    alert.getId(), e.getMessage(), e);
            throw new RuntimeException("Discord 알림 전송 실패", e);
        }
    }

    /**
     * 제품 재입고 알림을 디스코드로 전송
     * @param webhookUrl 디스코드 웹훅 URL
     * @param product 재입고된 제품 정보
     */
    public void sendProductRestockAlert(String webhookUrl, Product product) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        // 웹훅 URL 형식 검증
        if (!DiscordUtils.isValidDiscordWebhookUrl(webhookUrl)) {
            log.warn("잘못된 디스코드 웹훅 URL 형식입니다. URL: {}...", DiscordUtils.maskWebhookUrl(webhookUrl));
            return;
        }

        log.info("디스코드 제품 재입고 알림 전송 시작 - 제품 ID: {}, 이름: {}", product.getId(), product.getName());

        try {
            // 재고 상태 이모지
            String stockEmoji = switch (product.getCurrentStatus()) {
                case IN_STOCK -> "💚";
                case OUT_OF_STOCK -> "❤️";
                case UNKNOWN -> "❓";
            };

            // 우선순위 이모지
            String priorityText = product.getPriority() == Product.Priority.URGENT ? "🔴 긴급" : "🟢 일반";

            // 가격 정보
            String priceText = product.getCurrentPrice() != null ?
                String.format("%,d원", product.getCurrentPrice().intValue()) : "정보 없음";

            // Discord Embed 형식으로 메시지 구성
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "🔔 " + product.getName() + " 재입고!");
            embed.put("url", product.getUrl());
            embed.put("color", DiscordConstants.EMBED_COLOR_GREEN);
            embed.put("description", "━━━━━━━━━━━━━━━");

            // 필드 추가
            Map<String, Object> field1 = new HashMap<>();
            field1.put("name", "재고");
            field1.put("value", stockEmoji + " 재고 있음");
            field1.put("inline", true);

            Map<String, Object> field2 = new HashMap<>();
            field2.put("name", "가격");
            field2.put("value", priceText);
            field2.put("inline", true);

            Map<String, Object> field3 = new HashMap<>();
            field3.put("name", "우선순위");
            field3.put("value", priorityText);
            field3.put("inline", true);

            embed.put("fields", List.of(field1, field2, field3));

            // Footer 추가
            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "웹 모니터링 시스템 - 제품 재입고 알림");
            embed.put("footer", footer);

            // 이미지 추가 (있는 경우)
            if (product.getImageUrl() != null && !product.getImageUrl().trim().isEmpty()) {
                Map<String, Object> thumbnail = new HashMap<>();
                thumbnail.put("url", product.getImageUrl());
                embed.put("thumbnail", thumbnail);
            }

            // 최종 요청 바디 구성
            Map<String, Object> body = new HashMap<>();
            body.put("embeds", List.of(embed));

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // 재시도 로직이 포함된 웹훅 POST 요청
            ResponseEntity<String> response = sendDiscordWebhookWithRetry(
                    webhookUrl, request, "재입고 알림 (" + product.getName() + ")");

            log.info("디스코드 제품 재입고 알림 전송 성공 - 제품: {}, HTTP 상태: {}",
                    product.getName(), response.getStatusCode().value());

        } catch (RuntimeException e) {
            log.error("디스코드 제품 재입고 알림 전송 실패 - 제품: {}, 에러: {}",
                    product.getName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("디스코드 제품 재입고 알림 전송 중 예상치 못한 오류 - 제품: {}, 에러: {}",
                    product.getName(), e.getMessage(), e);
            throw new RuntimeException("Discord 재입고 알림 전송 실패", e);
        }
    }

    /**
     * 간단한 텍스트 메시지를 디스코드로 전송
     * @param webhookUrl 디스코드 웹훅 URL
     * @param message 전송할 메시지
     */
    public void sendSimpleMessage(String webhookUrl, String message) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        // 웹훅 URL 형식 검증
        if (!DiscordUtils.isValidDiscordWebhookUrl(webhookUrl)) {
            log.warn("잘못된 디스코드 웹훅 URL 형식입니다. URL: {}...", DiscordUtils.maskWebhookUrl(webhookUrl));
            return;
        }

        log.info("디스코드 간단 메시지 전송 시작");

        try {
            // 간단한 메시지 형식
            Map<String, Object> body = new HashMap<>();
            body.put("content", message);

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // 재시도 로직이 포함된 웹훅 POST 요청
            ResponseEntity<String> response = sendDiscordWebhookWithRetry(
                    webhookUrl, request, "간단 메시지 전송");

            log.info("디스코드 간단 메시지 전송 성공 - HTTP 상태: {}",
                    response.getStatusCode().value());

        } catch (RuntimeException e) {
            log.error("디스코드 메시지 전송 실패 - 에러: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("디스코드 메시지 전송 중 예상치 못한 오류 - 에러: {}", e.getMessage(), e);
            throw new RuntimeException("Discord 메시지 전송 실패", e);
        }
    }

    // ========================================
    // RETRY LOGIC WITH ERROR HANDLING
    // ========================================

    /**
     * 재시도 로직이 포함된 Discord 웹훅 POST 요청
     * Circuit Breaker 적용: Discord API 장애 시 빠른 실패로 시스템 리소스 보호
     * @param webhookUrl 디스코드 웹훅 URL
     * @param request HTTP 요청 엔티티
     * @param context 로그용 컨텍스트 (예: "알림 전송", "재입고 알림")
     * @return ResponseEntity
     * @throws RuntimeException 모든 재시도 실패 시
     */
    @CircuitBreaker(name = "discord", fallbackMethod = "sendDiscordWebhookFallback")
    private ResponseEntity<String> sendDiscordWebhookWithRetry(
            String webhookUrl,
            HttpEntity<Map<String, Object>> request,
            String context) {

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    if (attempt > 1) {
                        log.info("{} 재시도 성공 - 시도: {}, HTTP 상태: {}",
                                context, attempt, response.getStatusCode().value());
                    }
                    return response;
                } else {
                    log.warn("{} 비정상 응답 - HTTP 상태: {}", context, response.getStatusCode().value());
                    lastException = new RuntimeException("HTTP " + response.getStatusCode().value());
                }

            } catch (HttpClientErrorException e) {
                // 4xx 클라이언트 오류
                lastException = e;

                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    // 429 Rate Limiting - 특별 처리
                    log.warn("{} Rate Limiting 감지 (429) - 60초 대기 후 재시도", context);
                    try {
                        Thread.sleep(RATE_LIMIT_WAIT_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(context + " - 대기 중 인터럽트 발생", ie);
                    }
                } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                    // 401, 403 - 재시도 불필요
                    log.error("{} 인증/권한 오류 ({}): {} - 재시도 중단",
                            context, e.getStatusCode().value(), e.getMessage());
                    throw new RuntimeException(context + " - 인증/권한 오류", e);
                } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    // 404 - 웹훅 URL 잘못됨
                    log.error("{} 웹훅 URL을 찾을 수 없음 (404) - 재시도 중단", context);
                    throw new RuntimeException(context + " - 웹훅 URL 없음", e);
                } else {
                    // 기타 4xx 오류
                    log.warn("{} 클라이언트 오류 ({}): {}", context, e.getStatusCode().value(), e.getMessage());
                }

            } catch (HttpServerErrorException e) {
                // 5xx 서버 오류 - 재시도 가능
                lastException = e;
                log.warn("{} Discord 서버 오류 ({}) - 재시도 가능: {}",
                        context, e.getStatusCode().value(), e.getMessage());

            } catch (ResourceAccessException e) {
                // 네트워크/타임아웃 오류 - 재시도 가능
                lastException = e;
                log.warn("{} 네트워크 오류 - 재시도 가능: {}", context, e.getMessage());

            } catch (Exception e) {
                // 기타 예상치 못한 오류
                lastException = e;
                log.error("{} 예상치 못한 오류: {}", context, e.getMessage(), e);
            }

            // 재시도 대기 (Exponential Backoff)
            if (attempt < MAX_RETRIES) {
                long waitTime = INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)); // 1s, 2s, 4s
                log.info("{} 재시도 대기 중... {}ms (시도: {}/{})", context, waitTime, attempt, MAX_RETRIES);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(context + " - 대기 중 인터럽트 발생", ie);
                }
            }
        }

        // 모든 재시도 실패
        String errorMsg = String.format("%s 실패 - 최대 재시도 횟수 초과 (%d회)", context, MAX_RETRIES);
        log.error(errorMsg, lastException);
        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * Circuit Breaker Fallback 메서드
     * Circuit이 OPEN 상태일 때 호출됩니다.
     */
    private ResponseEntity<String> sendDiscordWebhookFallback(
            String webhookUrl,
            HttpEntity<Map<String, Object>> request,
            String context,
            Throwable throwable) {
        log.error("Circuit Breaker OPEN: Discord API 호출 차단됨 (Context: {}) - 이유: {}",
                context, throwable.getMessage());
        throw new RuntimeException("Circuit Breaker OPEN: Discord API가 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.", throwable);
    }
}
