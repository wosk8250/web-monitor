package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Article;
import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.event.SseTransmissionFailureEvent;
import com.webmonitor.exception.resource.SiteNotFoundException;
import com.webmonitor.repository.ArticleRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorTransactionHandler {

    private final SiteRepository siteRepository;
    private final ArticleRepository articleRepository;
    private final KeywordRepository keywordRepository;
    private final AlertService alertService;
    private final SseService sseService;
    private final ArticleExtractorService articleExtractorService;
    private final ArticleSaverService articleSaverService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSiteMonitoringResult(Site site, Document document, String pageTitle, String pageText, String currentHash) {
        Site managedSite = siteRepository.findById(site.getId())
                .orElseThrow(() -> new SiteNotFoundException(site.getId()));

        boolean isFirstRun = (managedSite.getLastContentHash() == null);
        boolean contentChanged = detectContentChange(managedSite, currentHash);

        if (isFirstRun || contentChanged) {
            if (isFirstRun) {
                log.info("사이트 최초 모니터링 시작 (기존 게시글은 알림 없이 저장만 수행): {}", managedSite.getName());
            } else {
                log.info("사이트 내용 변경 감지: {}", managedSite.getName());
            }

            boolean articlesExtracted = extractAndProcessArticles(managedSite, document, isFirstRun);

            if (!articlesExtracted) {
                if (!isFirstRun && managedSite.getDetectContentChange()) {
                    createContentChangeAlert(managedSite, pageTitle, document.location());
                }
                if (!isFirstRun) {
                    log.info("개별 게시글 추출 실패, 전체 페이지 키워드 감지 모드로 전환: {}", managedSite.getName());
                    detectKeywords(managedSite, pageText, pageTitle, document.location());
                }
            }
        }

        managedSite.setLastCheckedAt(LocalDateTime.now());
        siteRepository.save(managedSite);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateLastCheckedTime(Long siteId) {
        siteRepository.findById(siteId).ifPresent(site -> {
            site.setLastCheckedAt(LocalDateTime.now());
            siteRepository.save(site);
        });
    }

    @Transactional
    public void detectKeywords(Site site, String pageText, String pageTitle, String detectedUrl) {
        List<Keyword> siteKeywords = keywordRepository.findBySiteAndActive(site, true);
        List<Keyword> globalKeywords = keywordRepository.findBySiteIsNullAndActive(true);

        List<Keyword> allKeywords = new ArrayList<>(siteKeywords);
        allKeywords.addAll(globalKeywords);

        if (allKeywords.isEmpty()) {
            return;
        }

        for (Keyword keyword : allKeywords) {
            if (pageText.contains(keyword.getKeyword())) {
                String keywordType = keyword.getSite() == null ? "공통" : "사이트";
                log.info("키워드 감지! 사이트: {}, 키워드: {} ({})",
                        site.getName(), keyword.getKeyword(), keywordType);
                createAlert(site, keyword, pageTitle, detectedUrl);
            }
        }
    }

    @Transactional
    public void createAlert(Site site, Keyword keyword, String pageTitle, String detectedUrl) {
        String message = String.format(
                "[%s] 키워드 '%s' 감지 - %s",
                site.getName(),
                keyword.getKeyword(),
                pageTitle != null && !pageTitle.trim().isEmpty() ? pageTitle : "제목 없음"
        );

        Alert alert = Alert.builder()
                .site(site)
                .keyword(keyword)
                .message(message)
                .pageTitle(pageTitle)
                .detectedUrl(detectedUrl)
                .sent(false)
                .build();

        saveAndBroadcastAlert(alert, "createAlert");
    }

    @Transactional
    public void createContentChangeAlert(Site site, String pageTitle, String detectedUrl) {
        String message = String.format(
                "[%s] 새 글 - %s",
                site.getName(),
                pageTitle != null && !pageTitle.trim().isEmpty() ? pageTitle : "제목 없음"
        );

        Alert alert = Alert.builder()
                .site(site)
                .keyword(null)
                .message(message)
                .pageTitle(pageTitle)
                .detectedUrl(detectedUrl)
                .sent(false)
                .build();

        saveAndBroadcastAlert(alert, "createContentChangeAlert");
    }

    private boolean detectContentChange(Site site, String currentHash) {
        String previousHash = site.getLastContentHash();

        if (previousHash == null) {
            site.setLastContentHash(currentHash);
            return false;
        }

        boolean changed = !previousHash.equals(currentHash);
        if (changed) {
            log.info("내용 변경 감지: Site ID = {}, Site Name = {}", site.getId(), site.getName());
            site.setLastContentHash(currentHash);
        }
        return changed;
    }

    private boolean extractAndProcessArticles(Site site, Document document, boolean isFirstRun) {
        try {
            Elements articleLinks = articleExtractorService.extractArticleLinks(site, document);

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

                    if (articleUrl.isEmpty()) {
                        continue;
                    }

                    String articleId = articleExtractorService.extractArticleId(articleUrl);

                    boolean alreadyExists;
                    if (articleId != null && !articleId.isEmpty()) {
                        alreadyExists = articleRepository.existsBySiteAndArticleId(site, articleId);
                    } else {
                        alreadyExists = articleRepository.existsBySiteAndArticleUrl(site, articleUrl);
                    }

                    if (!alreadyExists) {
                        if (!isFirstRun) {
                            log.info("신규 게시글 발견: {} - {}", articleTitle, articleUrl);
                        }

                        Article article = Article.builder()
                                .site(site)
                                .articleUrl(articleUrl)
                                .articleTitle(articleTitle)
                                .articleId(articleId)
                                .build();

                        if (!articleSaverService.saveIfNotDuplicate(article)) {
                            continue;
                        }

                        processArticleForKeywords(site, article, articleTitle, articleUrl, isFirstRun);
                        newArticleCount++;
                    }

                } catch (Exception e) {
                    log.error("게시글 처리 중 오류: {}", e.getMessage(), e);
                }
            }

            log.info("신규 게시글 처리 완료: {} 개 (사이트: {})", newArticleCount, site.getName());
            return true;

        } catch (Exception e) {
            log.error("게시글 추출 중 오류 발생: {} - {}", site.getName(), e.getMessage(), e);
            return false;
        }
    }

    private void processArticleForKeywords(Site site, Article article, String articleTitle, String articleUrl, boolean isFirstRun) {
        if (isFirstRun) {
            return;
        }

        List<Keyword> siteKeywords = site.getKeywords().stream()
                .filter(Keyword::getActive)
                .toList();

        List<Keyword> globalKeywords = keywordRepository.findBySiteIsNullAndActive(true);

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

        if (siteKeywords.isEmpty() && globalKeywords.isEmpty()) {
            createArticleAlert(site, null, articleTitle, articleUrl);
        }
    }

    private void createArticleAlert(Site site, Keyword keyword, String articleTitle, String articleUrl) {
        String message;
        if (keyword != null) {
            message = String.format("[%s] 키워드 '%s' 감지 - %s", site.getName(), keyword.getKeyword(), articleTitle);
        } else {
            message = String.format("[%s] 새 글 - %s", site.getName(), articleTitle);
        }

        Alert alert = Alert.builder()
                .site(site)
                .keyword(keyword)
                .message(message)
                .pageTitle(articleTitle)
                .detectedUrl(articleUrl)
                .sent(false)
                .build();

        Alert savedAlert = saveAndBroadcastAlert(alert, "createArticleAlert");
        log.info("게시글 Alert 생성 완료: ID = {}, 제목 = {}", savedAlert.getId(), articleTitle);
    }

    private Alert saveAndBroadcastAlert(Alert alert, String methodName) {
        Alert savedAlert = alertService.createAlert(alert);
        log.info("알림 생성 완료: {}", alert.getMessage());

        // SSE I/O를 트랜잭션 커밋 이후로 지연 — DB 커넥션 점유 최소화
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcastAlert(savedAlert, methodName);
                }
            });
        } else {
            broadcastAlert(savedAlert, methodName);
        }

        return savedAlert;
    }

    private void broadcastAlert(Alert alert, String methodName) {
        try {
            sseService.broadcastAlert(alert);
            log.info("실시간 알림 전송 완료: 알림 ID = {}", alert.getId());
        } catch (Exception e) {
            log.error("실시간 알림 전송 실패: {}", e.getMessage(), e);
            eventPublisher.publishEvent(new SseTransmissionFailureEvent(
                    this, alert.getId(), alert.getMessage(), e.getMessage(), methodName
            ));
        }
    }
}
