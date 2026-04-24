package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Article;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.ArticleRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 웹사이트 모니터링 및 키워드 감지를 처리하는 서비스
 */
@Service // Spring의 Service 컴포넌트로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class MonitorService {

    private final SiteRepository siteRepository;
    private final KeywordRepository keywordRepository;
    private final AlertRepository alertRepository;
    private final ArticleRepository articleRepository;
    private final SseService sseService; // SSE 실시간 알림 서비스

    /**
     * 모든 활성화된 사이트 모니터링 실행
     */
    @Transactional
    public void monitorAllActiveSites() {
        log.info("활성화된 사이트 모니터링 시작");

        List<Site> activeSites = siteRepository.findByActive(true);
        log.info("모니터링할 사이트 수: {}", activeSites.size());

        for (Site site : activeSites) {
            try {
                monitorSite(site);
            } catch (Exception e) {
                log.error("사이트 모니터링 중 오류 발생: {} - {}", site.getName(), e.getMessage(), e);
            }
        }

        log.info("모니터링 작업 완료");
    }

    /**
     * 특정 사이트 모니터링
     * @param site 모니터링할 사이트
     */
    @Transactional
    public void monitorSite(Site site) {
        log.info("사이트 모니터링 시작: {}", site.getName());

        try {
            // JSoup을 사용하여 웹페이지 크롤링
            Document document = crawlWebsite(site.getUrl());

            // 페이지 제목 추출
            String pageTitle = document.title();

            // 불필요한 태그 제거 (스크립트, 스타일, 네비게이션 요소 등)
            document.select("script, style, header, footer, nav, aside").remove();

            // 본문 내용만 텍스트 추출
            String pageText = document.body().text();

            // 해시값 계산 및 변경 감지
            String currentHash = calculateHash(pageText);
            boolean contentChanged = detectContentChange(site, currentHash);

            if (contentChanged) {
                log.info("사이트 내용 변경 감지: {}", site.getName());

                // 전체 페이지 변경 감지 옵션이 활성화된 경우 알림 생성
                if (site.getDetectContentChange()) {
                    createContentChangeAlert(site, pageTitle, document.location());
                }
            }

            // 개별 게시글 추출 시도 (articleSelector 설정 여부와 무관하게 시도)
            boolean articlesExtracted = extractAndProcessArticles(site, document);

            // 게시글 추출 실패 시 전체 페이지 키워드 감지로 fallback
            if (!articlesExtracted) {
                log.info("개별 게시글 추출 실패, 전체 페이지 키워드 감지 모드로 전환: {}", site.getName());
                detectKeywords(site, pageText, pageTitle, document.location());
            }

        } catch (IOException e) {
            log.error("사이트 크롤링 실패: {} - {}", site.getName(), e.getMessage());
        } catch (Exception e) {
            log.error("모니터링 중 예외 발생: {} - {}", site.getName(), e.getMessage(), e);
        }
    }

    /**
     * JSoup을 사용하여 웹사이트 크롤링
     * @param url 크롤링할 URL
     * @return JSoup Document 객체
     * @throws IOException 크롤링 실패 시
     */
    private Document crawlWebsite(String url) throws IOException {
        log.debug("웹사이트 크롤링: {}", url);

        // JSoup으로 웹페이지 가져오기
        // timeout: 10초, userAgent: 일반 브라우저로 위장
        Document document = Jsoup.connect(url)
                .timeout(10000)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get();

        log.debug("크롤링 완료: {}", url);
        return document;
    }

    /**
     * 문자열의 SHA-256 해시값 계산
     * @param content 해시를 계산할 문자열
     * @return 16진수 문자열로 변환된 해시값
     */
    private String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));

            // 바이트 배열을 16진수 문자열로 변환
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("해시 알고리즘을 찾을 수 없습니다: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 사이트 내용 변경 감지 (DB 기반)
     * @param site 사이트 정보
     * @param currentHash 현재 해시값
     * @return 변경 여부 (true: 변경됨, false: 변경 안됨)
     */
    private boolean detectContentChange(Site site, String currentHash) {
        String previousHash = site.getLastContentHash();

        // 이전 해시값이 없으면 최초 실행
        if (previousHash == null) {
            log.debug("최초 모니터링 - 해시값 저장: Site ID = {}, Site Name = {}", site.getId(), site.getName());
            site.setLastContentHash(currentHash);
            siteRepository.save(site);
            return false;
        }

        // 해시값 비교
        boolean changed = !previousHash.equals(currentHash);

        if (changed) {
            log.info("내용 변경 감지: Site ID = {}, Site Name = {}", site.getId(), site.getName());
            site.setLastContentHash(currentHash); // 새로운 해시값 저장
            siteRepository.save(site);
        }

        return changed;
    }

    /**
     * 페이지에서 키워드 감지
     * @param site 사이트 정보
     * @param pageText 페이지 전체 텍스트
     * @param pageTitle 페이지 제목
     * @param detectedUrl 감지된 URL
     */
    @Transactional
    public void detectKeywords(Site site, String pageText, String pageTitle, String detectedUrl) {
        // 해당 사이트의 활성화된 키워드 조회
        List<Keyword> siteKeywords = keywordRepository.findBySiteAndActive(site, true);

        // 전체 공통 키워드 조회 (site가 null이고 active인 키워드)
        List<Keyword> globalKeywords = keywordRepository.findByActive(true).stream()
                .filter(keyword -> keyword.getSite() == null)
                .toList();

        // 사이트별 키워드와 전체 공통 키워드 합치기
        List<Keyword> allKeywords = new java.util.ArrayList<>(siteKeywords);
        allKeywords.addAll(globalKeywords);

        if (allKeywords.isEmpty()) {
            log.debug("사이트에 등록된 활성 키워드가 없습니다: {}", site.getName());
            return;
        }

        log.debug("키워드 감지 시작: {} (사이트 키워드: {}, 공통 키워드: {})",
                site.getName(), siteKeywords.size(), globalKeywords.size());

        // 각 키워드에 대해 검사
        for (Keyword keyword : allKeywords) {
            if (pageText.contains(keyword.getKeyword())) {
                String keywordType = keyword.getSite() == null ? "공통" : "사이트";
                log.info("키워드 감지! 사이트: {}, 키워드: {} ({})",
                        site.getName(), keyword.getKeyword(), keywordType);
                createAlert(site, keyword, pageTitle, detectedUrl);
            }
        }
    }

    /**
     * 알림 생성 및 실시간 전송
     * @param site 사이트 정보
     * @param keyword 감지된 키워드
     * @param pageTitle 페이지 제목
     * @param detectedUrl 감지된 URL
     */
    @Transactional
    public void createAlert(Site site, Keyword keyword, String pageTitle, String detectedUrl) {
        // 알림 메시지 생성
        String message = String.format(
                "[%s] 키워드 '%s' 감지 - %s",
                site.getName(),
                keyword.getKeyword(),
                pageTitle != null && !pageTitle.trim().isEmpty() ? pageTitle : "제목 없음"
        );

        // Alert 엔티티 생성
        Alert alert = Alert.builder()
                .site(site)
                .keyword(keyword)
                .message(message)
                .pageTitle(pageTitle)
                .detectedUrl(detectedUrl)
                .sent(false) // 아직 전송되지 않음
                .build();

        // 데이터베이스에 저장
        Alert savedAlert = alertRepository.save(alert);
        log.info("알림 생성 완료: {}", message);

        // SSE를 통해 모든 연결된 클라이언트에게 실시간 알림 전송
        // 디스코드 웹훅도 자동으로 전송됨
        try {
            sseService.broadcastAlert(savedAlert);
            log.info("실시간 알림 전송 완료: 알림 ID = {}", savedAlert.getId());
        } catch (Exception e) {
            log.error("실시간 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 전체 페이지 변경 감지 알림 생성 및 실시간 전송
     * @param site 사이트 정보
     * @param pageTitle 페이지 제목
     * @param detectedUrl 감지된 URL
     */
    @Transactional
    public void createContentChangeAlert(Site site, String pageTitle, String detectedUrl) {
        // 알림 메시지 생성
        String message = String.format(
                "[%s] 새 글 - %s",
                site.getName(),
                pageTitle != null && !pageTitle.trim().isEmpty() ? pageTitle : "제목 없음"
        );

        // Alert 엔티티 생성 (키워드 없이)
        Alert alert = Alert.builder()
                .site(site)
                .keyword(null) // 전체 페이지 변경 감지는 키워드 없음
                .message(message)
                .pageTitle(pageTitle)
                .detectedUrl(detectedUrl)
                .sent(false) // 아직 전송되지 않음
                .build();

        // 데이터베이스에 저장
        Alert savedAlert = alertRepository.save(alert);
        log.info("내용 변경 알림 생성 완료: {}", message);

        // SSE를 통해 모든 연결된 클라이언트에게 실시간 알림 전송
        // 디스코드 웹훅도 자동으로 전송됨
        try {
            sseService.broadcastAlert(savedAlert);
            log.info("실시간 알림 전송 완료: 알림 ID = {}", savedAlert.getId());
        } catch (Exception e) {
            log.error("실시간 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 게시글 링크 추출 (articleSelector 또는 fallback 패턴 사용)
     * @param site 사이트 정보
     * @param document JSoup Document
     * @return 추출된 게시글 링크 Elements
     */
    private Elements tryExtractArticleLinks(Site site, Document document) {
        String selector = site.getArticleSelector();

        // articleSelector가 설정되어 있으면 사용
        if (selector != null && !selector.trim().isEmpty()) {
            Elements links = document.select(selector);
            if (!links.isEmpty()) {
                log.info("설정된 선택자로 게시글 링크 추출: {} 개 (선택자: {})", links.size(), selector);
                return links;
            }
            log.warn("설정된 선택자로 링크를 찾을 수 없음. Fallback 시도: {}", selector);
        }

        // Fallback: 일반적인 패턴 시도
        log.info("articleSelector가 비어있거나 추출 실패. Fallback 패턴 시도: {}", site.getName());

        String[] fallbackPatterns = {
                "a.ub-word",              // 디시인사이드 갤러리
                "tr.ub-content a",        // 디시인사이드 (테이블 행)
                "a[href*='/board/']",     // 게시판 링크 패턴
                "a[href*='/post/']",      // 포스트 링크 패턴
                "a[href*='/article/']",   // 아티클 링크 패턴
                "a.title",                // 제목 클래스
                "a.post-title",           // 포스트 제목
                "a.post-link",            // 포스트 링크
                "a.article-title"         // 아티클 제목
        };

        for (String pattern : fallbackPatterns) {
            Elements links = document.select(pattern);
            if (!links.isEmpty()) {
                log.info("Fallback 패턴 '{}' 성공: {} 개 링크 발견", pattern, links.size());
                return links;
            }
        }

        // 마지막 시도: 모든 a 태그에서 조건에 맞는 것만 필터링
        log.info("모든 Fallback 패턴 실패. 일반 a 태그 필터링 시도: {}", site.getName());
        Elements allLinks = document.select("a[href]");
        Elements filtered = new Elements();

        String baseUrl = site.getUrl();
        for (Element link : allLinks) {
            String href = link.attr("abs:href");  // 절대 URL
            String text = link.text().trim();

            // 조건: 같은 도메인 내부 링크이고, 텍스트가 있고, 최소 길이 이상
            if (!text.isEmpty() && text.length() >= 3) {
                // 내부 링크 확인 (base URL과 같은 도메인)
                if (href.startsWith(baseUrl) || !href.startsWith("http")) {
                    // 이미지나 파일 다운로드 링크 제외
                    if (!href.matches(".*\\.(jpg|jpeg|png|gif|pdf|zip|exe)$")) {
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
        return new Elements();  // 빈 Elements 반환
    }

    /**
     * 개별 게시글 추출 및 처리
     * @param site 사이트 정보
     * @param document JSoup Document
     */
    private boolean extractAndProcessArticles(Site site, Document document) {
        try {
            // articleSelector 또는 fallback 패턴으로 게시글 링크 추출
            Elements articleLinks = tryExtractArticleLinks(site, document);

            // 추출된 링크가 없으면 실패
            if (articleLinks.isEmpty()) {
                log.warn("게시글 링크를 찾을 수 없습니다: {}", site.getName());
                return false;
            }

            log.info("발견한 게시글 링크 수: {} (사이트: {})", articleLinks.size(), site.getName());

            int newArticleCount = 0;

            for (Element linkElement : articleLinks) {
                try {
                    String articleUrl = linkElement.absUrl("href");
                    String articleTitle = linkElement.text();

                    // URL이 비어있으면 스킵
                    if (articleUrl.isEmpty()) {
                        continue;
                    }

                    // 게시글 ID 추출 (URL에서)
                    String articleId = extractArticleId(articleUrl);

                    // 이미 저장된 게시글인지 확인
                    boolean alreadyExists = false;
                    if (articleId != null && !articleId.isEmpty()) {
                        alreadyExists = articleRepository.existsBySiteAndArticleId(site, articleId);
                    } else {
                        alreadyExists = articleRepository.existsBySiteAndArticleUrl(site, articleUrl);
                    }

                    // 신규 게시글인 경우에만 처리
                    if (!alreadyExists) {
                        log.info("신규 게시글 발견: {} - {}", articleTitle, articleUrl);

                        // Article 엔티티 생성 및 저장
                        Article article = Article.builder()
                                .site(site)
                                .articleUrl(articleUrl)
                                .articleTitle(articleTitle)
                                .articleId(articleId)
                                .build();
                        articleRepository.save(article);

                        // 키워드 확인 후 Alert 생성
                        processArticleForKeywords(site, article, articleTitle, articleUrl);

                        newArticleCount++;
                    }

                } catch (Exception e) {
                    log.error("게시글 처리 중 오류: {}", e.getMessage(), e);
                }
            }

            log.info("신규 게시글 처리 완료: {} 개 (사이트: {})", newArticleCount, site.getName());
            return true;  // 게시글 추출 성공

        } catch (Exception e) {
            log.error("게시글 추출 중 오류 발생: {} - {}", site.getName(), e.getMessage(), e);
            return false;  // 게시글 추출 실패
        }
    }

    /**
     * URL에서 게시글 ID 추출
     * @param url 게시글 URL
     * @return 게시글 ID (추출 실패 시 null)
     */
    private String extractArticleId(String url) {
        try {
            // 일반적인 게시글 ID 패턴들
            // 예: /board/123, /post/456, /article/789, ?id=123, ?no=456
            Pattern[] patterns = {
                    Pattern.compile("/board/(\\d+)"),
                    Pattern.compile("/post/(\\d+)"),
                    Pattern.compile("/article/(\\d+)"),
                    Pattern.compile("/view/(\\d+)"),
                    Pattern.compile("[?&]id=(\\d+)"),
                    Pattern.compile("[?&]no=(\\d+)"),
                    Pattern.compile("[?&]num=(\\d+)"),
                    Pattern.compile("/\\d+/(\\d+)"),  // /category/123 형태
            };

            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(url);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }

            // ID 추출 실패 시 null 반환
            return null;

        } catch (Exception e) {
            log.warn("게시글 ID 추출 실패: {} - {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 게시글에서 키워드 확인 및 Alert 생성
     * @param site 사이트
     * @param article 게시글
     * @param articleTitle 게시글 제목
     * @param articleUrl 게시글 URL
     */
    private void processArticleForKeywords(Site site, Article article, String articleTitle, String articleUrl) {
        // 사이트별 키워드 조회
        List<Keyword> siteKeywords = site.getKeywords().stream()
                .filter(Keyword::getActive)
                .toList();

        // 전체 공통 키워드 조회
        List<Keyword> globalKeywords = keywordRepository.findByActive(true).stream()
                .filter(keyword -> keyword.getSite() == null)
                .toList();

        // 제목에서 키워드 검색
        for (Keyword keyword : siteKeywords) {
            if (articleTitle.contains(keyword.getKeyword())) {
                createArticleAlert(site, keyword, articleTitle, articleUrl);
            }
        }

        for (Keyword keyword : globalKeywords) {
            if (articleTitle.contains(keyword.getKeyword())) {
                createArticleAlert(site, keyword, articleTitle, articleUrl);
            }
        }

        // 키워드가 없으면 단순 신규 글 알림
        if (siteKeywords.isEmpty() && globalKeywords.isEmpty()) {
            createArticleAlert(site, null, articleTitle, articleUrl);
        }
    }

    /**
     * 게시글 Alert 생성
     * @param site 사이트
     * @param keyword 키워드 (null 가능)
     * @param articleTitle 게시글 제목
     * @param articleUrl 게시글 URL
     */
    private void createArticleAlert(Site site, Keyword keyword, String articleTitle, String articleUrl) {
        String message;
        if (keyword != null) {
            message = String.format(
                    "[%s] 키워드 '%s' 감지 - %s",
                    site.getName(),
                    keyword.getKeyword(),
                    articleTitle
            );
        } else {
            message = String.format(
                    "[%s] 새 글 - %s",
                    site.getName(),
                    articleTitle
            );
        }

        Alert alert = Alert.builder()
                .site(site)
                .keyword(keyword)
                .message(message)
                .pageTitle(articleTitle)
                .detectedUrl(articleUrl)
                .sent(false)
                .build();

        Alert savedAlert = alertRepository.save(alert);
        log.info("게시글 Alert 생성 완료: ID = {}, 제목 = {}", savedAlert.getId(), articleTitle);

        // SSE 실시간 알림 전송
        try {
            sseService.broadcastAlert(savedAlert);
            log.info("실시간 알림 전송 완료: 알림 ID = {}", savedAlert.getId());
        } catch (Exception e) {
            log.error("실시간 알림 전송 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 사이트의 저장된 해시값 초기화 (DB 기반)
     * @param siteId 사이트 ID
     */
    public void resetSiteHash(Long siteId) {
        siteRepository.findById(siteId).ifPresent(site -> {
            site.setLastContentHash(null);
            siteRepository.save(site);
            log.info("사이트 해시값 초기화: Site ID = {}, Site Name = {}", siteId, site.getName());
        });
    }

    /**
     * 모든 사이트의 저장된 해시값 초기화 (DB 기반)
     */
    public void resetAllHashes() {
        List<Site> allSites = siteRepository.findAll();
        for (Site site : allSites) {
            site.setLastContentHash(null);
            siteRepository.save(site);
        }
        log.info("모든 사이트 해시값 초기화: {} 개 사이트", allSites.size());
    }
}
