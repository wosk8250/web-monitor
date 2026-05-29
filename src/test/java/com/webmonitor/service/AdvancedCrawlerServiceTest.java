package com.webmonitor.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AdvancedCrawlerService 테스트
 */
class AdvancedCrawlerServiceTest {

    private AdvancedCrawlerService crawlerService;

    @BeforeEach
    void setUp() {
        crawlerService = new AdvancedCrawlerService();
    }

    // ===============================
    // isValidUrl() 테스트
    // ===============================

    @Test
    @DisplayName("isValidUrl - HTTP URL 유효성 검증 성공")
    void isValidUrl_Http_ReturnsTrue() {
        // Given
        String url = "http://example.com/page";

        // When
        boolean result = crawlerService.isValidUrl(url);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValidUrl - HTTPS URL 유효성 검증 성공")
    void isValidUrl_Https_ReturnsTrue() {
        // Given
        String url = "https://example.com/page";

        // When
        boolean result = crawlerService.isValidUrl(url);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValidUrl - 잘못된 URL 형식")
    void isValidUrl_InvalidFormat_ReturnsFalse() {
        // Given
        String url = "not-a-valid-url";

        // When
        boolean result = crawlerService.isValidUrl(url);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValidUrl - FTP 프로토콜 거부")
    void isValidUrl_FtpProtocol_ReturnsFalse() {
        // Given
        String url = "ftp://example.com/file";

        // When
        boolean result = crawlerService.isValidUrl(url);

        // Then
        assertThat(result).isFalse();
    }

    // ===============================
    // extractDomain() 테스트
    // ===============================

    @Test
    @DisplayName("extractDomain - 도메인 추출 성공")
    void extractDomain_ValidUrl_ReturnsDomain() {
        // Given
        String url = "https://www.example.com/path/to/page";

        // When
        String domain = crawlerService.extractDomain(url);

        // Then
        assertThat(domain).isEqualTo("www.example.com");
    }

    @Test
    @DisplayName("extractDomain - 잘못된 URL null 반환")
    void extractDomain_InvalidUrl_ReturnsNull() {
        // Given
        String url = "not-a-valid-url";

        // When
        String domain = crawlerService.extractDomain(url);

        // Then
        assertThat(domain).isNull();
    }

    @Test
    @DisplayName("extractDomain - 서브도메인 포함 도메인 추출")
    void extractDomain_WithSubdomain_ReturnsFullDomain() {
        // Given
        String url = "https://api.sub.example.com/endpoint";

        // When
        String domain = crawlerService.extractDomain(url);

        // Then
        assertThat(domain).isEqualTo("api.sub.example.com");
    }

    // ===============================
    // extractImageUrls() 테스트
    // ===============================

    @Test
    @DisplayName("extractImageUrls - 이미지 URL 추출 성공")
    void extractImageUrls_WithImages_ReturnsImageUrls() {
        // Given
        String html = "<html><body>" +
                "<img src='/image1.jpg'>" +
                "<img src='/image2.png'>" +
                "<div><img src='/image3.gif'></div>" +
                "</body></html>";
        Document document = Jsoup.parse(html, "https://example.com");

        // When
        List<String> imageUrls = crawlerService.extractImageUrls(document);

        // Then
        assertThat(imageUrls).hasSize(3);
        assertThat(imageUrls).containsExactly(
                "https://example.com/image1.jpg",
                "https://example.com/image2.png",
                "https://example.com/image3.gif"
        );
    }

    @Test
    @DisplayName("extractImageUrls - 이미지 없을 때 빈 리스트 반환")
    void extractImageUrls_NoImages_ReturnsEmptyList() {
        // Given
        String html = "<html><body><p>No images here</p></body></html>";
        Document document = Jsoup.parse(html);

        // When
        List<String> imageUrls = crawlerService.extractImageUrls(document);

        // Then
        assertThat(imageUrls).isEmpty();
    }

    @Test
    @DisplayName("extractImageUrls - src 없는 img 태그 무시")
    void extractImageUrls_ImgWithoutSrc_IgnoresIt() {
        // Given
        String html = "<html><body>" +
                "<img src='/valid.jpg'>" +
                "<img>" +  // src 없음
                "</body></html>";
        Document document = Jsoup.parse(html, "https://example.com");

        // When
        List<String> imageUrls = crawlerService.extractImageUrls(document);

        // Then
        assertThat(imageUrls).hasSize(1);
        assertThat(imageUrls).containsExactly("https://example.com/valid.jpg");
    }

    // ===============================
    // extractImageUrls(selector) 테스트
    // ===============================

    @Test
    @DisplayName("extractImageUrls(selector) - 특정 선택자 이미지만 추출")
    void extractImageUrlsWithSelector_SpecificSelector_ReturnsMatchingImages() {
        // Given
        String html = "<html><body>" +
                "<article><img src='/article-img.jpg'></article>" +
                "<div class='thumbnail'><img src='/thumb.jpg'></div>" +
                "<footer><img src='/footer-img.jpg'></footer>" +
                "</body></html>";
        Document document = Jsoup.parse(html, "https://example.com");

        // When
        List<String> articleImages = crawlerService.extractImageUrls(document, "article img");

        // Then
        assertThat(articleImages).hasSize(1);
        assertThat(articleImages).containsExactly("https://example.com/article-img.jpg");
    }

    @Test
    @DisplayName("extractImageUrls(selector) - 선택자와 일치하는 이미지 없을 때")
    void extractImageUrlsWithSelector_NoMatch_ReturnsEmptyList() {
        // Given
        String html = "<html><body><div><img src='/img.jpg'></div></body></html>";
        Document document = Jsoup.parse(html);

        // When
        List<String> images = crawlerService.extractImageUrls(document, "article img");

        // Then
        assertThat(images).isEmpty();
    }

    @Test
    @DisplayName("extractImageUrls(selector) - 클래스 선택자로 이미지 추출")
    void extractImageUrlsWithSelector_ClassSelector_ReturnsImages() {
        // Given
        String html = "<html><body>" +
                "<img class='thumbnail' src='/thumb1.jpg'>" +
                "<img class='thumbnail' src='/thumb2.jpg'>" +
                "<img class='other' src='/other.jpg'>" +
                "</body></html>";
        Document document = Jsoup.parse(html, "https://example.com");

        // When
        List<String> thumbnails = crawlerService.extractImageUrls(document, "img.thumbnail");

        // Then
        assertThat(thumbnails).hasSize(2);
        assertThat(thumbnails).containsExactly(
                "https://example.com/thumb1.jpg",
                "https://example.com/thumb2.jpg"
        );
    }

    // ===============================
    // checkRobotsTxt() 테스트
    // ===============================

    @Test
    @DisplayName("checkRobotsTxt - robots.txt 없을 때 허용")
    void checkRobotsTxt_NoRobotsTxt_ReturnsTrue() {
        // Given
        String url = "https://httpbin.org/html";  // robots.txt가 없는 URL

        // When
        boolean allowed = crawlerService.checkRobotsTxt(url);

        // Then
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("checkRobotsTxt - 잘못된 URL 형식 허용")
    void checkRobotsTxt_InvalidUrl_ReturnsTrue() {
        // Given
        String url = "not-a-valid-url";

        // When
        boolean allowed = crawlerService.checkRobotsTxt(url);

        // Then
        assertThat(allowed).isTrue();  // 파싱 실패 시 허용으로 간주
    }

    // ===============================
    // rateLimitedCrawl() 테스트
    // ===============================

    @Test
    @DisplayName("rateLimitedCrawl - Rate Limiting 1초 간격 확인")
    void rateLimitedCrawl_SameDomain_WaitsOneSecond() throws IOException {
        // Given
        String url = "https://httpbin.org/html";

        // When
        long startTime = System.currentTimeMillis();
        crawlerService.rateLimitedCrawl(url);  // 첫 번째 요청
        crawlerService.rateLimitedCrawl(url);  // 두 번째 요청 (1초 대기 예상)
        long endTime = System.currentTimeMillis();

        long elapsedTime = endTime - startTime;

        // Then
        assertThat(elapsedTime).isGreaterThanOrEqualTo(1000);  // 최소 1초 이상 걸림
        assertThat(elapsedTime).isLessThan(5000);  // 5초 이내 (타이밍 테스트 여유 허용)
    }

    @Test
    @DisplayName("rateLimitedCrawl - 다른 도메인은 대기 없음")
    void rateLimitedCrawl_DifferentDomains_NoWait() throws IOException {
        // Given
        String url1 = "https://httpbin.org/html";
        String url2 = "https://example.com";

        // When
        long startTime = System.currentTimeMillis();
        crawlerService.rateLimitedCrawl(url1);  // 첫 번째 도메인
        crawlerService.rateLimitedCrawl(url2);  // 다른 도메인 (대기 없음)
        long endTime = System.currentTimeMillis();

        long elapsedTime = endTime - startTime;

        // Then
        assertThat(elapsedTime).isLessThan(2000);  // 2초 이내 (대기 없이 빠르게 완료)
    }
}
