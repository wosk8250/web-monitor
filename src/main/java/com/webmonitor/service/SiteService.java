package com.webmonitor.service;

import com.webmonitor.domain.Site;
import com.webmonitor.dto.SiteRequest;
import com.webmonitor.dto.SiteResponse;
import com.webmonitor.exception.resource.SiteNotFoundException;
import com.webmonitor.repository.SiteRepository;
import com.webmonitor.util.XssSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SiteService {

    private final SiteRepository siteRepository;
    private final AlertService alertService;
    private final KeywordService keywordService;
    private final ArticleService articleService;

    @Transactional
    @CacheEvict(value = {"sites", "allSites", "activeSites"}, allEntries = true)
    public SiteResponse createSite(SiteRequest request) {
        log.info("사이트 등록 시작: {}", request.getName());

        Site site = Site.builder()
                .name(request.getName())
                .url(request.getUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Site savedSite = siteRepository.save(site);
        log.info("사이트 등록 완료: ID = {}", savedSite.getId());
        return SiteResponse.from(savedSite);
    }

    @Transactional
    @CacheEvict(value = {"sites", "allSites", "activeSites"}, allEntries = true)
    public SiteResponse updateSite(Long id, SiteRequest request) {
        log.info("사이트 수정 시작: ID = {}", id);

        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new SiteNotFoundException(id));

        if (request.getName() != null && !request.getName().isBlank()) site.setName(request.getName());
        if (request.getUrl() != null && !request.getUrl().isBlank()) site.setUrl(request.getUrl());
        if (request.getActive() != null) site.setActive(request.getActive());

        Site saved = siteRepository.save(site);
        log.info("사이트 수정 완료: ID = {}", id);
        return SiteResponse.from(saved);
    }

    @Transactional
    @CacheEvict(value = {"sites", "allSites", "activeSites"}, allEntries = true)
    public void deleteSite(Long id) {
        log.info("사이트 삭제 시작: ID = {}", id);

        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new SiteNotFoundException(id));

        // FK 제약 순서 보장: 자식 먼저 벌크 삭제 후 부모 삭제
        // (JPA cascade는 flush 순서에 따라 부모가 먼저 삭제될 수 있어 명시적 사전 삭제 필요)
        alertService.deleteAlertsBySiteId(id);
        keywordService.deleteKeywordsBySiteId(id);
        articleService.deleteArticlesBySite(site);

        siteRepository.delete(site);
        log.info("사이트 삭제 완료: ID = {}", id);
    }

    @Cacheable(value = "sites", key = "#id")
    public Optional<SiteResponse> getSiteById(Long id) {
        log.debug("사이트 조회 (DB): ID = {}", id);
        return siteRepository.findById(id).map(SiteResponse::from);
    }

    @Cacheable(value = "allSites")
    public List<SiteResponse> getAllSites() {
        log.debug("전체 사이트 조회 (DB)");
        return siteRepository.findAll().stream()
                .map(SiteResponse::from)
                .toList();
    }

    @Cacheable(value = "activeSites")
    public List<SiteResponse> getActiveSites() {
        log.debug("활성화된 사이트 조회 (DB)");
        return siteRepository.findByActive(true).stream()
                .map(SiteResponse::from)
                .toList();
    }

    public List<SiteResponse> getSitesByActive(boolean active) {
        log.debug("사이트 활성화 상태 필터 조회 (DB): active={}", active);
        if (active) return getActiveSites();
        return siteRepository.findByActive(false).stream()
                .map(SiteResponse::from)
                .toList();
    }

    public List<SiteResponse> searchSitesByName(String name) {
        log.debug("사이트 이름 검색: {}", name);
        return siteRepository.findByNameContaining(XssSanitizer.sanitize(name)).stream()
                .map(SiteResponse::from)
                .toList();
    }

}
