package com.webmonitor.service;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.repository.SiteRepository;
import com.webmonitor.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitorService {

    private final SiteRepository siteRepository;
    private final WebCrawlerService webCrawlerService;
    private final MonitorTransactionHandler monitorTransactionHandler;

    @Autowired
    @Qualifier("siteMonitorExecutor")
    private Executor siteMonitorExecutor;

    // 현재 모니터링 중인 사이트 ID — 스케줄러 중첩 실행 시 동일 사이트 중복 dispatch 방지
    private final ConcurrentHashMap<Long, Boolean> inProgressSites = new ConcurrentHashMap<>();

    public void monitorAllActiveSites() {
        log.info("활성화된 사이트 모니터링 시작");

        List<Site> activeSites = siteRepository.findByActiveWithKeywords(true);
        log.debug("모니터링할 사이트 수: {}", activeSites.size());

        for (Site site : activeSites) {
            Long siteId = site.getId();
            if (shouldCheckSite(site) && inProgressSites.putIfAbsent(siteId, Boolean.TRUE) == null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        monitorSite(site);
                    } catch (Exception e) {
                        log.error("사이트 모니터링 중 오류 발생: {} - {}", site.getName(), e.getMessage(), e);
                    } finally {
                        inProgressSites.remove(siteId);
                    }
                }, siteMonitorExecutor);
            }
        }

        log.info("모니터링 dispatch 완료 (비동기 실행 중)");
    }

    private boolean shouldCheckSite(Site site) {
        if (site.getLastCheckedAt() == null) {
            return true;
        }
        long minutesElapsed = Duration.between(site.getLastCheckedAt(), LocalDateTime.now()).toMinutes();
        return minutesElapsed >= site.getCheckIntervalMinutes();
    }

    public void monitorSite(Site site) {
        log.info("사이트 모니터링 시작: {}", site.getName());

        try {
            Document document = webCrawlerService.crawl(site.getUrl());

            String pageTitle = document.title();
            document.select("script, style, header, footer, nav, aside").remove();
            String pageText = document.body().text();
            String currentHash = HashUtils.sha256(pageText);

            monitorTransactionHandler.processSiteMonitoringResult(site, document, pageTitle, pageText, currentHash);

        } catch (IOException e) {
            log.error("사이트 크롤링 실패: {} - {}", site.getName(), e.getMessage());
            monitorTransactionHandler.updateLastCheckedTime(site.getId());
        } catch (Exception e) {
            log.error("모니터링 중 예외 발생: {} - {}", site.getName(), e.getMessage(), e);
            monitorTransactionHandler.updateLastCheckedTime(site.getId());
        }
    }

    public void detectKeywords(Site site, String pageText, String pageTitle, String detectedUrl) {
        monitorTransactionHandler.detectKeywords(site, pageText, pageTitle, detectedUrl);
    }

    public void createAlert(Site site, Keyword keyword, String pageTitle, String detectedUrl) {
        monitorTransactionHandler.createAlert(site, keyword, pageTitle, detectedUrl);
    }

    public void createContentChangeAlert(Site site, String pageTitle, String detectedUrl) {
        monitorTransactionHandler.createContentChangeAlert(site, pageTitle, detectedUrl);
    }

    @Transactional
    public void resetSiteHash(Long siteId) {
        siteRepository.findById(siteId).ifPresent(site -> {
            site.setLastContentHash(null);
            siteRepository.save(site);
            log.info("사이트 해시값 초기화: Site ID = {}, Site Name = {}", siteId, site.getName());
        });
    }

    @Transactional
    public void resetAllHashes() {
        int updated = siteRepository.resetAllContentHashes();
        log.info("모든 사이트 해시값 초기화: {} 개 사이트", updated);
    }
}
