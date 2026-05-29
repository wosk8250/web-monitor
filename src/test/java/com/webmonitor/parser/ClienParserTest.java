package com.webmonitor.parser;

import com.webmonitor.dto.ArticleInfo;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ClienParser 테스트
 */
class ClienParserTest {

    private ClienParser parser;

    @BeforeEach
    void setUp() {
        parser = new ClienParser();
    }

    @Test
    @DisplayName("getSupportedDomain - 지원 도메인 반환")
    void getSupportedDomain_ReturnsCorrectDomain() {
        // When
        String domain = parser.getSupportedDomain();

        // Then
        assertThat(domain).isEqualTo("clien.net");
    }

    @Test
    @DisplayName("parseArticles - 클리앙 게시글 파싱 성공")
    void parseArticles_ValidHtml_ReturnsArticles() {
        // Given - 클리앙의 실제 HTML 구조를 모방
        String html = "<html><body>" +
                // 첫 번째 게시글
                "<div class='list_item'>" +
                "  <a class='list_subject' href='/service/board/park/12345'>클리앙 게시글 제목 1</a>" +
                "  <span class='nickname'>작성자1</span>" +
                "  <span class='hit'>1,234</span>" +
                "  <span class='recommend'>56</span>" +
                "  <span class='comment_count'>78</span>" +
                "</div>" +
                // 두 번째 게시글
                "<div class='list_item'>" +
                "  <a class='list_subject' href='/service/board/park/12346'>클리앙 게시글 제목 2</a>" +
                "  <span class='nickname'>작성자2</span>" +
                "  <span class='hit'>500</span>" +
                "  <span class='recommend'>10</span>" +
                "  <span class='comment_count'>20</span>" +
                "</div>" +
                "</body></html>";

        Document document = Jsoup.parse(html, "https://www.clien.net/");

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).hasSize(2);

        // 첫 번째 게시글 검증
        ArticleInfo first = articles.get(0);
        assertThat(first.getTitle()).isEqualTo("클리앙 게시글 제목 1");
        assertThat(first.getUrl()).isEqualTo("https://www.clien.net/service/board/park/12345");
        assertThat(first.getAuthor()).isEqualTo("작성자1");
        assertThat(first.getPoints()).isEqualTo(56);
        assertThat(first.getComments()).isEqualTo(78);

        // 두 번째 게시글 검증
        ArticleInfo second = articles.get(1);
        assertThat(second.getTitle()).isEqualTo("클리앙 게시글 제목 2");
        assertThat(second.getUrl()).isEqualTo("https://www.clien.net/service/board/park/12346");
        assertThat(second.getAuthor()).isEqualTo("작성자2");
        assertThat(second.getPoints()).isEqualTo(10);
        assertThat(second.getComments()).isEqualTo(20);
    }

    @Test
    @DisplayName("parseArticles - 추천/댓글 없는 게시글")
    void parseArticles_NoPointsAndComments_HandlesGracefully() {
        // Given
        String html = "<html><body>" +
                "<div class='list_item'>" +
                "  <a class='list_subject' href='/service/board/test/111'>테스트 게시글</a>" +
                "  <span class='nickname'>테스터</span>" +
                "</div>" +
                "</body></html>";

        Document document = Jsoup.parse(html, "https://www.clien.net/");

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).hasSize(1);
        ArticleInfo article = articles.get(0);
        assertThat(article.getTitle()).isEqualTo("테스트 게시글");
        assertThat(article.getAuthor()).isEqualTo("테스터");
        assertThat(article.getPoints()).isNull();
        assertThat(article.getComments()).isNull();
    }

    @Test
    @DisplayName("parseArticles - 게시글이 없을 때 빈 리스트 반환")
    void parseArticles_NoArticles_ReturnsEmptyList() {
        // Given
        String html = "<html><body></body></html>";
        Document document = Jsoup.parse(html);

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).isEmpty();
    }

    @Test
    @DisplayName("parseArticles - 제목이 없는 게시글 스킵")
    void parseArticles_NoTitle_SkipsArticle() {
        // Given
        String html = "<html><body>" +
                "<div class='list_item'>" +
                "  <span class='nickname'>작성자</span>" +
                "</div>" +
                "</body></html>";

        Document document = Jsoup.parse(html);

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).isEmpty();
    }

    @Test
    @DisplayName("parseArticles - 숫자 파싱 (쉼표 포함)")
    void parseArticles_NumberWithComma_ParsesCorrectly() {
        // Given
        String html = "<html><body>" +
                "<div class='list_item'>" +
                "  <a class='list_subject' href='/test'>테스트</a>" +
                "  <span class='hit'>12,345</span>" +
                "  <span class='recommend'>1,000</span>" +
                "  <span class='comment_count'>999</span>" +
                "</div>" +
                "</body></html>";

        Document document = Jsoup.parse(html, "https://www.clien.net/");

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).hasSize(1);
        ArticleInfo article = articles.get(0);
        assertThat(article.getPoints()).isEqualTo(1000);
        assertThat(article.getComments()).isEqualTo(999);
    }

    @Test
    @DisplayName("getLatestArticleUrl - 최신 게시글 URL 반환")
    void getLatestArticleUrl_ValidHtml_ReturnsFirstArticleUrl() {
        // Given
        String html = "<html><body>" +
                "<div class='list_item'>" +
                "  <a class='list_subject' href='/latest'>최신 글</a>" +
                "</div>" +
                "<div class='list_item'>" +
                "  <a class='list_subject' href='/older'>이전 글</a>" +
                "</div>" +
                "</body></html>";

        Document document = Jsoup.parse(html, "https://www.clien.net/");

        // When
        String latestUrl = parser.getLatestArticleUrl(document);

        // Then
        assertThat(latestUrl).isEqualTo("https://www.clien.net/latest");
    }

    @Test
    @DisplayName("getLatestArticleUrl - 게시글이 없을 때 null 반환")
    void getLatestArticleUrl_NoArticles_ReturnsNull() {
        // Given
        String html = "<html><body></body></html>";
        Document document = Jsoup.parse(html);

        // When
        String latestUrl = parser.getLatestArticleUrl(document);

        // Then
        assertThat(latestUrl).isNull();
    }

    @Test
    @DisplayName("parseArticles - 절대 URL 변환")
    void parseArticles_RelativeUrl_ConvertsToAbsolute() {
        // Given
        String html = "<html><body>" +
                "<div class='list_item'>" +
                "  <a class='list_subject' href='/service/board/test/123'>상대 URL 테스트</a>" +
                "</div>" +
                "</body></html>";

        Document document = Jsoup.parse(html, "https://www.clien.net/");

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).hasSize(1);
        assertThat(articles.get(0).getUrl()).isEqualTo("https://www.clien.net/service/board/test/123");
    }
}
