package com.webmonitor.service;

import com.webmonitor.util.UrlUtils;
import com.webmonitor.util.WebCrawlerConstants;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * HTTP 크롤링 전담 서비스
 * 재시도(Exponential Backoff), URL 검증, HTTP 상태 코드 처리를 담당한다.
 */
@Service
@Slf4j
public class WebCrawlerService {

    public Document crawl(String url) throws IOException {
        validateUrl(url);

        int attempt = 0;
        IOException lastException = null;

        while (attempt < WebCrawlerConstants.MAX_RETRY_ATTEMPTS) {
            attempt++;
            try {
                return attemptCrawl(url);
            } catch (HttpStatusException e) {
                handleHttpStatusException(e, url);
                lastException = e;
            } catch (SocketTimeoutException e) {
                log.warn("크롤링 타임아웃 (시도 {}/{}): {}", attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url);
                lastException = e;
            } catch (UnknownHostException e) {
                log.error("호스트를 찾을 수 없음: {}", url);
                throw new IOException("호스트를 찾을 수 없습니다: " + url, e);
            } catch (ConnectException e) {
                log.warn("연결 실패 (시도 {}/{}): {} - {}", attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url, e.getMessage());
                lastException = e;
            } catch (IOException e) {
                log.warn("크롤링 실패 (시도 {}/{}): {} - {}", attempt, WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url, e.getMessage());
                lastException = e;
            }

            if (attempt < WebCrawlerConstants.MAX_RETRY_ATTEMPTS) {
                performRetryDelay(attempt, url);
            }
        }

        log.error("크롤링 최종 실패 ({}번 시도): {}", WebCrawlerConstants.MAX_RETRY_ATTEMPTS, url);
        throw new IOException("크롤링 최종 실패 after " + WebCrawlerConstants.MAX_RETRY_ATTEMPTS + " attempts: " + url, lastException);
    }

    private Document attemptCrawl(String url) throws IOException {
        String userAgent = WebCrawlerConstants.USER_AGENTS[
                ThreadLocalRandom.current().nextInt(WebCrawlerConstants.USER_AGENTS.length)];
        return Jsoup.connect(url)
                .timeout(WebCrawlerConstants.TIMEOUT_DEFAULT_MS)
                .maxBodySize(WebCrawlerConstants.MAX_BODY_SIZE_BYTES)
                .userAgent(userAgent)
                .followRedirects(true)
                .get();
    }

    private void validateUrl(String url) throws IOException {
        try {
            UrlUtils.validateUrl(url);
        } catch (IllegalArgumentException e) {
            log.error("URL 검증 실패: {} - {}", url, e.getMessage());
            throw new IOException("유효하지 않은 URL입니다: " + e.getMessage(), e);
        }
    }

    private void handleHttpStatusException(HttpStatusException e, String url) throws IOException {
        int statusCode = e.getStatusCode();
        log.warn("HTTP 상태 코드 오류: {} - {}", statusCode, url);
        switch (statusCode) {
            case 404 -> throw new IOException("페이지를 찾을 수 없습니다 (404): " + url, e);
            case 429 -> log.warn("요청 제한 (429) - 대기 후 재시도: {}", url);
            case 500, 502, 503 -> log.warn("서버 오류 ({}) - 재시도: {}", statusCode, url);
            default -> throw new IOException("HTTP 오류 " + statusCode + ": " + url, e);
        }
    }

    private void performRetryDelay(int attempt, String url) throws IOException {
        long delayMs = WebCrawlerConstants.INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("크롤링 재시도 중 중단됨: " + url, ie);
        }
    }
}
