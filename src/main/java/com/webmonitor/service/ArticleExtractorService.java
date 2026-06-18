package com.webmonitor.service;

import com.webmonitor.domain.Site;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 게시글 링크 추출 및 게시글 ID 파싱 전담 서비스
 */
@Service
@Slf4j
public class ArticleExtractorService {

    private static final String[] FALLBACK_SELECTORS = {
            "a.ub-word",
            "tr.ub-content a",
            "a[href*='/board/']",
            "a[href*='/post/']",
            "a[href*='/article/']",
            "a.title",
            "a.post-title",
            "a.post-link",
            "a.article-title"
    };

    private static final Pattern BINARY_EXT_PATTERN =
            Pattern.compile(".*\\.(jpg|jpeg|png|gif|pdf|zip|exe)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern[] ARTICLE_ID_PATTERNS = {
            Pattern.compile("/board/(\\d+)"),
            Pattern.compile("/post/(\\d+)"),
            Pattern.compile("/article/(\\d+)"),
            Pattern.compile("/view/(\\d+)"),
            Pattern.compile("[?&]id=(\\d+)"),
            Pattern.compile("[?&]no=(\\d+)"),
            Pattern.compile("[?&]num=(\\d+)"),
            Pattern.compile("/\\d+/(\\d+)")
    };

    public Elements extractArticleLinks(Site site, Document document) {
        String selector = site.getArticleSelector();

        if (selector != null && !selector.trim().isEmpty()) {
            Elements links = document.select(selector);
            if (!links.isEmpty()) {
                log.info("설정된 선택자로 게시글 링크 추출: {} 개 (선택자: {})", links.size(), selector);
                return links;
            }
            log.warn("설정된 선택자로 링크를 찾을 수 없음. Fallback 시도: {}", selector);
        }

        log.info("Fallback 패턴 시도: {}", site.getName());

        for (String pattern : FALLBACK_SELECTORS) {
            Elements links = document.select(pattern);
            if (!links.isEmpty()) {
                log.info("Fallback 패턴 '{}' 성공: {} 개 링크 발견", pattern, links.size());
                return links;
            }
        }

        log.info("모든 Fallback 패턴 실패. 일반 a 태그 필터링 시도: {}", site.getName());
        Elements allLinks = document.select("a[href]");
        Elements filtered = new Elements();
        String baseUrl = site.getUrl();

        for (Element link : allLinks) {
            String href = link.attr("abs:href");
            if (!href.startsWith("http")) continue; // mailto:, javascript: 등 비HTTP 제외
            String text = link.text().trim();
            if (!text.isEmpty() && text.length() >= 3) {
                if (href.startsWith(baseUrl)) {
                    if (!BINARY_EXT_PATTERN.matcher(href).matches()) {
                        filtered.add(link);
                    }
                }
            }
        }

        if (!filtered.isEmpty()) {
            log.info("일반 a 태그 필터링 성공: {} 개 링크 발견", filtered.size());
            return filtered;
        }

        log.warn("모든 추출 시도 실패: {}", site.getName());
        return new Elements();
    }

    public String extractArticleId(String url) {
        try {
            for (Pattern pattern : ARTICLE_ID_PATTERNS) {
                Matcher matcher = pattern.matcher(url);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("게시글 ID 추출 실패: {} - {}", url, e.getMessage());
            return null;
        }
    }
}
