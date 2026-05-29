package com.webmonitor.parser;

import com.webmonitor.dto.ArticleInfo;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Hacker News 전용 파서
 * https://news.ycombinator.com/
 */
@Component
@Slf4j
public class HackerNewsParser implements SiteParser {

    private static final String BASE_URL = "https://news.ycombinator.com";

    @Override
    public List<ArticleInfo> parseArticles(Document document) {
        List<ArticleInfo> articles = new ArrayList<>();

        // Hacker News의 게시글은 <tr class="athing">로 시작
        Elements articleRows = document.select("tr.athing");

        log.debug("Hacker News: {} 개의 게시글 발견", articleRows.size());

        for (Element articleRow : articleRows) {
            // 게시글 ID와 제목 요소를 미리 선언 (예외 로깅용)
            String articleId = null;
            Element titleElement = null;

            try {
                // 게시글 ID 추출
                articleId = articleRow.attr("id");

                // 제목과 URL 추출 (<span class="titleline"> > <a>)
                titleElement = articleRow.selectFirst("span.titleline > a");
                if (titleElement == null) {
                    log.warn("제목을 찾을 수 없습니다 (ID: {})", articleId);
                    continue;
                }

                String title = titleElement.text();
                String url = titleElement.attr("href");

                // 상대 URL이면 절대 URL로 변환 (슬래시 중복 방지)
                if (!url.startsWith("http")) {
                    url = url.startsWith("/") ? BASE_URL + url : BASE_URL + "/" + url;
                }

                // 다음 행(<tr>)에서 포인트, 작성자, 댓글 정보 추출
                Element nextRow = articleRow.nextElementSibling();
                Integer points = null;
                String author = null;
                Integer comments = null;

                if (nextRow != null) {
                    // 포인트 추출 (<span class="score">)
                    Element scoreElement = nextRow.selectFirst("span.score");
                    if (scoreElement != null) {
                        String scoreText = scoreElement.text(); // 예: "123 points"
                        points = parsePoints(scoreText);
                    }

                    // 작성자 추출 (<a class="hnuser">)
                    Element authorElement = nextRow.selectFirst("a.hnuser");
                    if (authorElement != null) {
                        author = authorElement.text();
                    }

                    // 댓글 수 추출 (마지막 <a> 태그에서 "comments" 포함)
                    Elements links = nextRow.select("a");
                    for (Element link : links) {
                        String linkText = link.text();
                        if (linkText.contains("comment")) {
                            comments = parseComments(linkText);
                            break;
                        }
                    }
                }

                // ArticleInfo 생성
                ArticleInfo article = ArticleInfo.builder()
                        .title(title)
                        .url(url)
                        .author(author)
                        .points(points)
                        .comments(comments)
                        .build();

                articles.add(article);

                log.debug("파싱 완료: {}", article);

            } catch (Exception e) {
                log.error("게시글 파싱 중 오류 발생 (ID: {}, URL: {})", articleId,
                          titleElement != null ? titleElement.attr("href") : "unknown", e);
            }
        }

        log.info("Hacker News: 총 {} 개의 게시글 파싱 완료", articles.size());
        return articles;
    }

    /**
     * 포인트 문자열에서 숫자 추출
     * 예: "123 points" -> 123
     */
    private Integer parsePoints(String scoreText) {
        try {
            return Integer.parseInt(scoreText.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 댓글 문자열에서 숫자 추출
     * 예: "42 comments" -> 42
     * 예: "discuss" -> 0
     */
    private Integer parseComments(String commentText) {
        try {
            if (commentText.equals("discuss")) {
                return 0;
            }
            return Integer.parseInt(commentText.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getSupportedDomain() {
        return "news.ycombinator.com";
    }
}
