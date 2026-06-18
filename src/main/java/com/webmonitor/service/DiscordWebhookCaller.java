package com.webmonitor.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Discord 웹훅 HTTP 호출을 @Component로 분리 — @CircuitBreaker AOP 프록시가 self-call을 우회하는 문제 해결.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscordWebhookCaller {

    private final RestTemplate restTemplate;

    private static final int MAX_RETRIES = 3;

    @Value("${discord.webhook.rate-limit-wait-ms:60000}")
    private long rateLimitWaitMs;

    @Value("${discord.webhook.retry-initial-delay-ms:1000}")
    private long retryInitialDelayMs;

    @CircuitBreaker(name = "discord", fallbackMethod = "sendDiscordWebhookFallback")
    public ResponseEntity<String> sendDiscordWebhookWithRetry(
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
                lastException = e;

                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("{} Rate Limiting 감지 (429) - {}ms 대기 후 재시도", context, rateLimitWaitMs);
                    try {
                        Thread.sleep(rateLimitWaitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(context + " - 대기 중 인터럽트 발생", ie);
                    }
                } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                    log.error("{} 인증/권한 오류 ({}): {} - 재시도 중단",
                            context, e.getStatusCode().value(), e.getMessage());
                    throw new RuntimeException(context + " - 인증/권한 오류", e);
                } else if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                    log.error("{} 웹훅 URL을 찾을 수 없음 (404) - 재시도 중단", context);
                    throw new RuntimeException(context + " - 웹훅 URL 없음", e);
                } else {
                    log.warn("{} 클라이언트 오류 ({}): {}", context, e.getStatusCode().value(), e.getMessage());
                }

            } catch (HttpServerErrorException e) {
                lastException = e;
                log.warn("{} Discord 서버 오류 ({}) - 재시도 가능: {}",
                        context, e.getStatusCode().value(), e.getMessage());

            } catch (ResourceAccessException e) {
                lastException = e;
                log.warn("{} 네트워크 오류 - 재시도 가능: {}", context, e.getMessage());

            } catch (Exception e) {
                lastException = e;
                log.error("{} 예상치 못한 오류: {}", context, e.getMessage(), e);
            }

            if (attempt < MAX_RETRIES) {
                long waitTime = retryInitialDelayMs * (1L << (attempt - 1));
                log.info("{} 재시도 대기 중... {}ms (시도: {}/{})", context, waitTime, attempt, MAX_RETRIES);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(context + " - 대기 중 인터럽트 발생", ie);
                }
            }
        }

        String errorMsg = String.format("%s 실패 - 최대 재시도 횟수 초과 (%d회)", context, MAX_RETRIES);
        log.error(errorMsg, lastException);
        throw new RuntimeException(errorMsg, lastException);
    }

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
