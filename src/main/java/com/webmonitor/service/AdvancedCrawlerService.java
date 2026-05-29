package com.webmonitor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.webmonitor.util.WebCrawlerConstants;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 고급 크롤링 기능을 제공하는 서비스
 * - 페이지네이션 자동화
 * - robots.txt 체크
 * - Rate Limiting
 * - 이미지 URL 추출
 */
@Service
@Slf4j
public class AdvancedCrawlerService {


    // 도메인별 마지막 요청 시간을 추적 (Rate Limiting용)
    // Caffeine Cache: 메모리 누수 방지 (최대 1000개, 1시간 TTL)
    private final Cache<String, Long> lastRequestTime = Caffeine.newBuilder()
            .maximumSize(WebCrawlerConstants.RATE_LIMIT_CACHE_SIZE)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();


    /**
     * 여러 페이지를 자동으로 크롤링 (페이지네이션 지원)
     *
     * @param baseUrl 기본 URL (예: https://example.com/posts?page=)
     * @param maxPages 크롤링할 최대 페이지 수
     * @param pageParam 페이지 번호 파라미터 이름 (예: "page")
     * @return 모든 페이지의 Document 리스트
     */
    public List<Document> crawlMultiplePages(String baseUrl, int maxPages, String pageParam) {
        List<Document> documents = new ArrayList<>();
        log.info("페이지네이션 크롤링 시작: baseUrl={}, maxPages={}", baseUrl, maxPages);

        for (int page = 1; page <= maxPages; page++) {
            try {
                // URL 구성: baseUrl에 페이지 번호 추가
                String pageUrl;
                if (baseUrl.contains("?")) {
                    pageUrl = baseUrl + "&" + pageParam + "=" + page;
                } else {
                    pageUrl = baseUrl + "?" + pageParam + "=" + page;
                }

                log.debug("페이지 {} 크롤링 중: {}", page, pageUrl);

                // Rate Limiting 적용하여 크롤링
                Document doc = rateLimitedCrawl(pageUrl);
                documents.add(doc);

                log.debug("페이지 {} 크롤링 완료", page);

            } catch (IOException e) {
                log.error("페이지 {} 크롤링 실패: {}", page, e.getMessage());
                // 실패해도 계속 진행 (다음 페이지 시도)
            }
        }

        log.info("페이지네이션 크롤링 완료: 총 {} 페이지", documents.size());
        return documents;
    }

    /**
     * Rate Limiting이 적용된 크롤링
     * 동일 도메인에 대한 요청 간 최소 대기 시간을 보장
     *
     * @param url 크롤링할 URL
     * @return JSoup Document
     * @throws IOException 크롤링 실패 시
     */
    public Document rateLimitedCrawl(String url) throws IOException {
        try {
            // URL에서 도메인 추출
            URI uri = new URI(url);
            String domain = uri.getHost();

            // 동일 도메인에 대한 이전 요청 시간 확인 (Cache는 thread-safe)
            synchronized (lastRequestTime) {
                Long lastRequest = lastRequestTime.getIfPresent(domain);
                long currentTime = System.currentTimeMillis();

                if (lastRequest != null) {
                    long timeSinceLastRequest = currentTime - lastRequest;

                    // 최소 대기 시간이 지나지 않았으면 대기
                    if (timeSinceLastRequest < WebCrawlerConstants.DEFAULT_RATE_LIMIT_MS) {
                        long waitTime = WebCrawlerConstants.DEFAULT_RATE_LIMIT_MS - timeSinceLastRequest;
                        log.debug("Rate Limiting: {}ms 대기 중... (도메인: {})", waitTime, domain);

                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Rate Limiting 대기 중 중단됨", e);
                        }
                    }
                }

                // 현재 요청 시간 기록
                lastRequestTime.put(domain, System.currentTimeMillis());
            }

        } catch (URISyntaxException e) {
            log.warn("URL 파싱 실패 (Rate Limiting 스킵): {}", url);
        }

        // 실제 크롤링 수행 (ThreadLocalRandom: thread-safe)
        String userAgent = WebCrawlerConstants.USER_AGENTS[ThreadLocalRandom.current().nextInt(WebCrawlerConstants.USER_AGENTS.length)];

        try {
            return Jsoup.connect(url)
                    .timeout(WebCrawlerConstants.TIMEOUT_DEFAULT_MS)
                    .userAgent(userAgent)
                    .followRedirects(true)
                    .get();

        } catch (SocketTimeoutException e) {
            log.error("Rate limited 크롤링 타임아웃: {} - 5초 초과", url);
            throw new IOException("크롤링 타임아웃: " + url, e);

        } catch (UnknownHostException e) {
            log.error("호스트를 찾을 수 없음: {}", url);
            throw new IOException("호스트를 찾을 수 없습니다: " + url, e);

        } catch (ConnectException e) {
            log.error("서버 연결 실패: {} - {}", url, e.getMessage());
            throw new IOException("서버 연결 실패: " + url, e);
        }
    }

    /**
     * robots.txt 체크 - 크롤링 허용 여부 확인
     *
     * @param url 체크할 URL
     * @return 크롤링 허용 여부 (true: 허용, false: 거부)
     */
    public boolean checkRobotsTxt(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            // scheme이나 host가 null이면 허용으로 간주
            if (scheme == null || host == null) {
                log.debug("URL 파싱 결과 scheme 또는 host가 null (허용으로 간주): {}", url);
                return true;
            }

            String robotsUrl = scheme + "://" + host + "/robots.txt";

            log.debug("robots.txt 확인: {}", robotsUrl);

            // robots.txt 파일 가져오기
            Document robotsDoc = Jsoup.connect(robotsUrl)
                    .timeout(WebCrawlerConstants.TIMEOUT_SHORT_MS)
                    .userAgent(WebCrawlerConstants.USER_AGENTS[0])
                    .ignoreContentType(true)  // plain text로 처리
                    .get();

            String robotsContent = robotsDoc.text();

            // 간단한 robots.txt 파싱
            // User-agent: * 섹션에서 Disallow 규칙 확인
            String[] lines = robotsContent.split("\n");
            boolean checkingGeneralAgent = false;

            for (String line : lines) {
                line = line.trim();

                // User-agent: * 섹션 시작
                if (line.toLowerCase().startsWith("user-agent:")) {
                    checkingGeneralAgent = line.contains("*");
                }

                // Disallow 규칙 확인 (User-agent: * 섹션에서만)
                if (checkingGeneralAgent && line.toLowerCase().startsWith("disallow:")) {
                    String disallowedPath = line.substring("disallow:".length()).trim();

                    // URL 경로가 Disallow된 경로로 시작하면 거부
                    if (!disallowedPath.isEmpty() && uri.getPath().startsWith(disallowedPath)) {
                        log.warn("robots.txt에 의해 크롤링 거부됨: {} (Disallow: {})", url, disallowedPath);
                        return false;
                    }
                }
            }

            log.debug("robots.txt 확인 완료: {} (허용)", url);
            return true;

        } catch (URISyntaxException e) {
            log.warn("URL 파싱 실패 (robots.txt 체크 스킵): {}", url);
            return true; // 파싱 실패 시 허용으로 간주

        } catch (SocketTimeoutException e) {
            log.debug("robots.txt 타임아웃: {} (허용으로 간주)", url);
            return true;

        } catch (UnknownHostException e) {
            log.debug("robots.txt 호스트를 찾을 수 없음: {} (허용으로 간주)", url);
            return true;

        } catch (ConnectException e) {
            log.debug("robots.txt 연결 실패: {} (허용으로 간주)", url);
            return true;

        } catch (IOException e) {
            log.debug("robots.txt 파일 없음 또는 접근 실패: {} (허용으로 간주)", url);
            return true; // robots.txt가 없으면 허용으로 간주
        }
    }

    /**
     * 페이지에서 모든 이미지 URL 추출
     *
     * @param document JSoup Document
     * @return 이미지 URL 리스트
     */
    public List<String> extractImageUrls(Document document) {
        List<String> imageUrls = new ArrayList<>();

        // <img> 태그에서 src 속성 추출
        Elements imgElements = document.select("img[src]");

        for (Element img : imgElements) {
            String src = img.absUrl("src");  // 절대 URL로 변환

            if (!src.isEmpty()) {
                imageUrls.add(src);
            }
        }

        log.debug("이미지 URL {} 개 추출", imageUrls.size());
        return imageUrls;
    }

    /**
     * 페이지에서 특정 선택자의 이미지 URL만 추출
     *
     * @param document JSoup Document
     * @param selector CSS 선택자 (예: "article img", ".thumbnail img")
     * @return 이미지 URL 리스트
     */
    public List<String> extractImageUrls(Document document, String selector) {
        List<String> imageUrls = new ArrayList<>();

        // 지정된 선택자로 img 태그 검색
        Elements imgElements = document.select(selector);

        for (Element img : imgElements) {
            String src = img.absUrl("src");

            if (!src.isEmpty()) {
                imageUrls.add(src);
            }
        }

        log.debug("이미지 URL {} 개 추출 (선택자: {})", imageUrls.size(), selector);
        return imageUrls;
    }

    /**
     * URL 유효성 검증
     *
     * @param url 검증할 URL
     * @return 유효 여부
     */
    public boolean isValidUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();

            // http 또는 https만 허용
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);

        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * URL에서 도메인 추출
     *
     * @param url URL
     * @return 도메인 (예: "example.com")
     */
    public String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            return uri.getHost();
        } catch (URISyntaxException e) {
            log.warn("URL 파싱 실패: {}", url);
            return null;
        }
    }
}
