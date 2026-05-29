package com.webmonitor.parser;

import com.webmonitor.dto.ArticleInfo;
import org.jsoup.nodes.Document;

import java.util.List;

/**
 * 사이트별 맞춤 파싱 로직을 정의하는 인터페이스
 */
public interface SiteParser {

    /**
     * 사이트 Document에서 게시글 목록을 파싱
     *
     * @param document JSoup Document
     * @return 파싱된 게시글 정보 리스트
     */
    List<ArticleInfo> parseArticles(Document document);

    /**
     * 사이트 Document에서 가장 최신 게시글 URL 반환
     *
     * @param document JSoup Document
     * @return 최신 게시글 URL (없으면 null)
     */
    default String getLatestArticleUrl(Document document) {
        List<ArticleInfo> articles = parseArticles(document);

        if (articles.isEmpty()) {
            return null;
        }

        return articles.get(0).getUrl();
    }

    /**
     * 이 파서가 지원하는 도메인 패턴 반환
     *
     * @return 도메인 패턴 (예: "news.ycombinator.com", "clien.net")
     */
    String getSupportedDomain();
}
