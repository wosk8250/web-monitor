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
 * 클리앙 전용 파서
 * https://www.clien.net/service/board/park
 */
@Component
@Slf4j
public class ClienParser implements SiteParser {

    private static final String BASE_URL = "https://www.clien.net";

    @Override
    public List<ArticleInfo> parseArticles(Document document) {
        List<ArticleInfo> articles = new ArrayList<>();

        // 클리앙 게시글 목록은 <div class="list_item">으로 구성
        Elements articleItems = document.select("div.list_item");

        log.debug("클리앙: {} 개의 게시글 발견", articleItems.size());

        for (Element item : articleItems) {
            // 제목 요소를 미리 선언 (예외 로깅용)
            Element titleElement = null;

            try {
                // 제목과 URL 추출
                titleElement = item.selectFirst("a.list_subject");
                if (titleElement == null) {
                    log.warn("제목을 찾을 수 없습니다");
                    continue;
                }

                String title = titleElement.text().trim();
                String url = titleElement.attr("href");

                // 상대 URL이면 절대 URL로 변환 (슬래시 중복 방지)
                if (!url.startsWith("http")) {
                    url = url.startsWith("/") ? BASE_URL + url : BASE_URL + "/" + url;
                }

                // 작성자 추출
                String author = null;
                Element authorElement = item.selectFirst("span.nickname");
                if (authorElement != null) {
                    author = authorElement.text().trim();
                }

                // 조회수 추출
                Integer views = null;
                Element viewsElement = item.selectFirst("span.hit");
                if (viewsElement != null) {
                    views = parseNumber(viewsElement.text());
                }

                // 추천수 추출
                Integer points = null;
                Element likesElement = item.selectFirst("span.recommend");
                if (likesElement != null) {
                    points = parseNumber(likesElement.text());
                }

                // 댓글 수 추출
                Integer comments = null;
                Element commentElement = item.selectFirst("span.comment_count");
                if (commentElement != null) {
                    comments = parseNumber(commentElement.text());
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
                String errorUrl = titleElement != null ? titleElement.attr("href") : "unknown";
                String errorTitle = titleElement != null ? titleElement.text().trim() : "unknown";
                log.error("게시글 파싱 중 오류 발생 (제목: {}, URL: {})", errorTitle, errorUrl, e);
            }
        }

        log.info("클리앙: 총 {} 개의 게시글 파싱 완료", articles.size());
        return articles;
    }

    /**
     * 문자열에서 숫자 추출
     * 예: "123" -> 123, "1,234" -> 1234
     */
    private Integer parseNumber(String text) {
        try {
            // 숫자가 아닌 문자 제거 (쉼표 등)
            String cleaned = text.replaceAll("[^0-9]", "");
            if (cleaned.isEmpty()) {
                return null;
            }
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String getSupportedDomain() {
        return "clien.net";
    }
}
