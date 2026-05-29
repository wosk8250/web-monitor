package com.webmonitor.util;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 웹 크롤링 유틸리티 클래스
 * URL 검증, HTML 다운로드 등 공통 크롤링 기능 제공
 */
@Component
@Slf4j
public class CrawlerUtils {



    /**
     * URL 보안 검증 (SSRF 공격 방어)
     *
     * @param urlString 검증할 URL
     * @throws IllegalArgumentException 유효하지 않은 URL인 경우
     */
    public void validateUrl(String urlString) {
        try {
            URL url = new URL(urlString);

            // 1. 프로토콜 제한 (HTTP/HTTPS만 허용)
            if (!url.getProtocol().matches("https?")) {
                throw new IllegalArgumentException("HTTP 또는 HTTPS 프로토콜만 허용됩니다: " + url.getProtocol());
            }

            // 2. 내부 IP 차단 (SSRF 방어)
            InetAddress address = InetAddress.getByName(url.getHost());
            if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
                throw new IllegalArgumentException("내부 IP 주소는 허용되지 않습니다: " + address.getHostAddress());
            }

            // 3. 포트 제한 (80, 443만 허용)
            int port = url.getPort() == -1 ? url.getDefaultPort() : url.getPort();
            if (port != 80 && port != 443) {
                throw new IllegalArgumentException("80 또는 443 포트만 허용됩니다: " + port);
            }

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("유효하지 않은 URL 형식입니다: " + urlString, e);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("호스트를 찾을 수 없습니다: " + urlString, e);
        }
    }

    /**
     * HTML 다운로드 (재시도 로직 포함)
     * Circuit Breaker 적용: 외부 API 호출 실패 시 빠른 실패로 cascading failure 방지
     *
     * @param url 다운로드할 URL
     * @return JSoup Document 객체
     * @throws IOException 다운로드 실패 시
     */
    @CircuitBreaker(name = "webCrawler", fallbackMethod = "fetchDocumentFallback")
    public Document fetchDocument(String url) throws IOException {
        log.debug("HTML 다운로드: {}", url);

        // SSRF 공격 방어를 위한 URL 검증
        validateUrl(url);

        int attempt = 0;
        IOException lastException = null;

        while (attempt < WebCrawlerConstants.MAX_RETRY_ATTEMPTS) {
            attempt++;
            try {
                // User-Agent 랜덤 선택 (크롤링 차단 방지)
                String userAgent = WebCrawlerConstants.USER_AGENTS[ThreadLocalRandom.current().nextInt(WebCrawlerConstants.USER_AGENTS.length)];

                log.debug("HTML 다운로드 시도 {}/{}: {}", attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url);

                Document document = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .timeout(WebCrawlerConstants.TIMEOUT_LONG_MS)
                        .maxBodySize(1024 * 1024)  // 1MB 제한 (메모리 보호)
                        .ignoreHttpErrors(false)    // HTTP 에러도 처리
                        .followRedirects(true)      // 리다이렉트 허용
                        .get();

                if (attempt > 1) {
                    log.info("HTML 다운로드 재시도 성공: 시도 {}, URL: {}", attempt, url);
                }
                return document;

            } catch (HttpStatusException e) {
                lastException = e;

                // HTTP 상태 코드별 처리
                switch (e.getStatusCode()) {
                    case 404:
                        log.error("페이지를 찾을 수 없음 (404): {}", url);
                        throw new IOException("페이지를 찾을 수 없습니다 (404): " + url, e);

                    case 429:
                        log.warn("요청 제한 (429) - 대기 후 재시도: {}", url);
                        break;

                    case 500:
                    case 502:
                    case 503:
                        log.warn("서버 오류 ({}): {} - 재시도 중", e.getStatusCode(), url);
                        break;

                    default:
                        log.warn("HTTP 오류 ({}): {} - 재시도 중", e.getStatusCode(), url);
                        break;
                }

            } catch (SocketTimeoutException e) {
                lastException = e;
                log.warn("HTML 다운로드 타임아웃 (시도 {}/{}): {}", attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url);

            } catch (UnknownHostException e) {
                lastException = e;
                log.error("URL 호스트를 찾을 수 없음: {}", url);
                throw new IOException("호스트를 찾을 수 없습니다: " + url, e);

            } catch (ConnectException e) {
                lastException = e;
                log.warn("URL 연결 실패 (시도 {}/{}): {} - {}",
                        attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url, e.getMessage());

            } catch (IOException e) {
                lastException = e;
                log.warn("HTML 다운로드 실패 (시도 {}/{}): {} - 오류: {}",
                        attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url, e.getMessage());
            }

            // 재시도 대기 (Exponential Backoff)
            if (attempt < WebCrawlerConstants.MAX_RETRY_ATTEMPTS) {
                long waitTime = WebCrawlerConstants.INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)); // 1s, 2s, 4s
                log.debug("재시도 대기 중... {}ms (시도: {}/{})", waitTime, attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS);
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("재시도 대기 중 인터럽트 발생", ie);
                }
            }
        }

        // 모든 재시도 실패
        log.error("HTML 다운로드 최대 재시도 횟수 초과: {}", url);
        throw new IOException("HTML 다운로드 실패 (최대 재시도 " + WebCrawlerConstants.MAX_RETRY_ATTEMPTS + "회): " + url, lastException);
    }

    /**
     * Circuit Breaker Fallback 메서드
     * Circuit이 OPEN 상태일 때 호출됩니다.
     *
     * @param url 요청 URL
     * @param throwable 발생한 예외
     * @throws IOException Circuit Breaker가 OPEN 상태임을 알리는 예외
     */
    private Document fetchDocumentFallback(String url, Throwable throwable) throws IOException {
        log.error("Circuit Breaker OPEN: 외부 웹사이트 크롤링 차단됨 (URL: {}) - 이유: {}",
                url, throwable.getMessage());
        throw new IOException("Circuit Breaker OPEN: 외부 웹사이트가 일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.", throwable);
    }

    /**
     * 랜덤 User-Agent 반환
     *
     * @return 랜덤하게 선택된 User-Agent 문자열
     */
    public String getRandomUserAgent() {
        return WebCrawlerConstants.USER_AGENTS[ThreadLocalRandom.current().nextInt(WebCrawlerConstants.USER_AGENTS.length)];
    }
}
