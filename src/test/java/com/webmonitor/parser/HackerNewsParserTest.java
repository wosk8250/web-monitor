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
 * HackerNewsParser 테스트
 */
class HackerNewsParserTest {

    private HackerNewsParser parser;

    @BeforeEach
    void setUp() {
        parser = new HackerNewsParser();
    }

    @Test
    @DisplayName("getSupportedDomain - 지원 도메인 반환")
    void getSupportedDomain_ReturnsCorrectDomain() {
        // When
        String domain = parser.getSupportedDomain();

        // Then
        assertThat(domain).isEqualTo("news.ycombinator.com");
    }

    @Test
    @DisplayName("parseArticles - Hacker News 게시글 파싱 성공")
    void parseArticles_ValidHtml_ReturnsArticles() {
        // Given - Hacker News의 실제 HTML 구조를 모방
        String html = "<html><body>" +
                "<table><tbody>" +
                // 첫 번째 게시글
                "<tr class='athing' id='123'>" +
                "  <td class='title'>" +
                "    <span class='titleline'>" +
                "      <a href='https://example.com/article1'>Show HN: My Cool Project</a>" +
                "    </span>" +
                "  </td>" +
                "</tr>" +
                "<tr>" +
                "  <td colspan='2'></td>" +
                "  <td class='subtext'>" +
                "    <span class='score'>150 points</span> by " +
                "    <a href='user?id=user1' class='hnuser'>user1</a> " +
                "    <a href='item?id=123'>42 comments</a>" +
                "  </td>" +
                "</tr>" +
                // 두 번째 게시글
                "<tr class='athing' id='124'>" +
                "  <td class='title'>" +
                "    <span class='titleline'>" +
                "      <a href='item?id=124'>Ask HN: What are you working on?</a>" +
                "    </span>" +
                "  </td>" +
                "</tr>" +
                "<tr>" +
                "  <td colspan='2'></td>" +
                "  <td class='subtext'>" +
                "    <span class='score'>25 points</span> by " +
                "    <a href='user?id=user2' class='hnuser'>user2</a> " +
                "    <a href='item?id=124'>discuss</a>" +
                "  </td>" +
                "</tr>" +
                "</tbody></table>" +
                "</body></html>";

        Document document = Jsoup.parse(html, "https://news.ycombinator.com/");

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).hasSize(2);

        // 첫 번째 게시글 검증 - 제목과 URL만 검증 (핵심 기능)
        ArticleInfo first = articles.get(0);
        assertThat(first.getTitle()).isEqualTo("Show HN: My Cool Project");
        assertThat(first.getUrl()).isEqualTo("https://example.com/article1");

        // 두 번째 게시글 검증 - 제목과 URL만 검증 (핵심 기능)
        ArticleInfo second = articles.get(1);
        assertThat(second.getTitle()).isEqualTo("Ask HN: What are you working on?");
        assertThat(second.getUrl()).isEqualTo("https://news.ycombinator.com/item?id=124");
    }

    @Test
    @DisplayName("parseArticles - 포인트와 댓글 없는 게시글")
    void parseArticles_NoPointsAndComments_HandlesGracefully() {
        // Given
        String html = "<html><body>" +
                "<table>" +
                "<tr class='athing' id='125'>" +
                "  <td class='title'>" +
                "    <span class='titleline'>" +
                "      <a href='https://example.com/article'>Test Article</a>" +
                "    </span>" +
                "  </td>" +
                "</tr>" +
                "<tr>" +
                "  <td colspan='2'></td>" +
                "  <td class='subtext'>" +
                "    <a href='user?id=testuser' class='hnuser'>testuser</a>" +
                "  </td>" +
                "</tr>" +
                "</table>" +
                "</body></html>";

        Document document = Jsoup.parse(html, "https://news.ycombinator.com/");

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).hasSize(1);
        ArticleInfo article = articles.get(0);
        assertThat(article.getTitle()).isEqualTo("Test Article");
        assertThat(article.getAuthor()).isEqualTo("testuser");
        assertThat(article.getPoints()).isNull();
        assertThat(article.getComments()).isNull();
    }

    @Test
    @DisplayName("parseArticles - 게시글이 없을 때 빈 리스트 반환")
    void parseArticles_NoArticles_ReturnsEmptyList() {
        // Given
        String html = "<html><body><table></table></body></html>";
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
                "<table>" +
                "<tr class='athing' id='126'>" +
                "  <td class='title'></td>" +
                "</tr>" +
                "</table>" +
                "</body></html>";

        Document document = Jsoup.parse(html);

        // When
        List<ArticleInfo> articles = parser.parseArticles(document);

        // Then
        assertThat(articles).isEmpty();
    }

    @Test
    @DisplayName("getLatestArticleUrl - 최신 게시글 URL 반환")
    void getLatestArticleUrl_ValidHtml_ReturnsFirstArticleUrl() {
        // Given
        String html = "<html><body>" +
                "<table>" +
                "<tr class='athing' id='127'>" +
                "  <td class='title'>" +
                "    <span class='titleline'>" +
                "      <a href='https://latest.com/article'>Latest Article</a>" +
                "    </span>" +
                "  </td>" +
                "</tr>" +
                "<tr class='athing' id='128'>" +
                "  <td class='title'>" +
                "    <span class='titleline'>" +
                "      <a href='https://older.com/article'>Older Article</a>" +
                "    </span>" +
                "  </td>" +
                "</tr>" +
                "</table>" +
                "</body></html>";

        Document document = Jsoup.parse(html);

        // When
        String latestUrl = parser.getLatestArticleUrl(document);

        // Then
        assertThat(latestUrl).isEqualTo("https://latest.com/article");
    }

    @Test
    @DisplayName("getLatestArticleUrl - 게시글이 없을 때 null 반환")
    void getLatestArticleUrl_NoArticles_ReturnsNull() {
        // Given
        String html = "<html><body><table></table></body></html>";
        Document document = Jsoup.parse(html);

        // When
        String latestUrl = parser.getLatestArticleUrl(document);

        // Then
        assertThat(latestUrl).isNull();
    }
}
