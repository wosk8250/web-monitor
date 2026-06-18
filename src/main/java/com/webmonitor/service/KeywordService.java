package com.webmonitor.service;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.KeywordRequest;
import com.webmonitor.dto.KeywordResponse;
import com.webmonitor.exception.resource.KeywordNotFoundException;
import com.webmonitor.exception.resource.SiteNotFoundException;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final SiteRepository siteRepository;
    private final AlertRepository alertRepository;

    /**
     * 새로운 키워드 등록.
     * 공백 키워드는 해당 사이트의 새글 감지를 활성화하며 Optional.empty() 를 반환한다.
     * 일반 키워드는 저장 후 Optional.of(dto) 를 반환한다.
     */
    @Transactional
    public Optional<KeywordResponse> createKeyword(KeywordRequest request) {
        log.info("키워드 등록 시작: keyword={}, siteId={}", request.getKeyword(), request.getSiteId());

        if (request.getKeyword() == null) {
            throw new IllegalArgumentException("키워드는 null일 수 없습니다");
        }

        if (request.getKeyword().isBlank()) {
            log.info("공백 키워드 감지 - 새글 감지 기능 활성화");

            if (request.getSiteId() == null) {
                throw new IllegalArgumentException("공백 키워드(새글 감지)는 특정 사이트에만 설정할 수 있습니다");
            }

            Site site = siteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> new SiteNotFoundException(request.getSiteId()));

            site.setDetectContentChange(true);
            siteRepository.save(site);
            log.info("사이트 '{}' 새글 감지 활성화 완료", site.getName());

            return Optional.empty();
        }

        Site site = null;
        if (request.getSiteId() != null) {
            site = siteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> new SiteNotFoundException(request.getSiteId()));
            log.debug("사이트 조회 완료: {}", site.getName());
        } else {
            log.debug("전체 공통 키워드로 등록합니다");
        }

        Keyword keyword = Keyword.builder()
                .keyword(request.getKeyword().strip())
                .site(site)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Keyword savedKeyword = keywordRepository.save(keyword);
        log.info("키워드 등록 완료: ID = {}, keyword = {}, site = {}",
                savedKeyword.getId(),
                savedKeyword.getKeyword(),
                site != null ? site.getName() : "전체 공통");

        return Optional.of(KeywordResponse.from(savedKeyword));
    }

    @Transactional
    public KeywordResponse updateKeyword(Long id, KeywordRequest request) {
        log.info("키워드 수정 시작: ID = {}", id);

        Keyword keyword = keywordRepository.findById(id)
                .orElseThrow(() -> new KeywordNotFoundException(id));

        if (request.getKeyword() != null && !request.getKeyword().isBlank()) keyword.setKeyword(request.getKeyword());
        if (request.getSiteId() != null) {
            Site site = siteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> new SiteNotFoundException(request.getSiteId()));
            keyword.setSite(site);
        }
        if (request.getActive() != null) keyword.setActive(request.getActive());

        Keyword saved = keywordRepository.save(keyword);
        log.info("키워드 수정 완료: ID = {}", id);
        return KeywordResponse.from(saved);
    }

    @Transactional
    public void deleteKeyword(Long id) {
        log.info("키워드 삭제 시작: ID = {}", id);

        if (!keywordRepository.existsById(id)) {
            throw new KeywordNotFoundException(id);
        }

        keywordRepository.deleteById(id);
        log.info("키워드 삭제 완료: ID = {}", id);
    }

    @Transactional
    public void deleteKeywordsBySiteId(Long siteId) {
        keywordRepository.deleteBySiteId(siteId);
        log.debug("사이트 ID {}의 모든 키워드 삭제 완료", siteId);
    }

    public Optional<KeywordResponse> getKeywordById(Long id) {
        log.debug("키워드 조회: ID = {}", id);
        return keywordRepository.findById(id).map(KeywordResponse::from);
    }

    public List<KeywordResponse> getAllKeywords() {
        log.debug("전체 키워드 조회");
        return keywordRepository.findAll().stream().map(KeywordResponse::from).toList();
    }

    public List<KeywordResponse> getActiveKeywords() {
        log.debug("활성화된 키워드 조회");
        return keywordRepository.findByActive(true).stream().map(KeywordResponse::from).toList();
    }

    public List<KeywordResponse> getKeywordsByActive(boolean active) {
        log.debug("키워드 활성화 상태 필터 조회: active={}", active);
        return keywordRepository.findByActive(active).stream().map(KeywordResponse::from).toList();
    }

    public List<KeywordResponse> getKeywordsBySite(Long siteId) {
        log.debug("사이트별 키워드 조회: siteId = {}", siteId);
        return keywordRepository.findBySiteId(siteId).stream().map(KeywordResponse::from).toList();
    }

    public List<KeywordResponse> getGlobalKeywords() {
        log.debug("전체 공통 키워드 조회");
        return keywordRepository.findBySiteIsNull().stream().map(KeywordResponse::from).toList();
    }

    @Transactional
    public Keyword addKeywordToSite(Site site, String keywordText) {
        if (keywordText == null || keywordText.isBlank()) {
            throw new IllegalArgumentException("키워드를 입력해주세요.");
        }
        String normalized = keywordText.strip();
        boolean exists = keywordRepository.findBySite(site).stream()
                .anyMatch(k -> k.getKeyword().equals(normalized));
        if (exists) {
            throw new IllegalArgumentException("이미 등록된 키워드입니다: " + normalized);
        }
        if (site.getDetectContentChange()) {
            site.setDetectContentChange(false);
            siteRepository.save(site);
        }
        Keyword keyword = Keyword.builder()
                .keyword(normalized)
                .site(site)
                .active(true)
                .build();
        return keywordRepository.save(keyword);
    }

    @Transactional
    public void removeKeywordFromSite(Site site, Keyword keyword) {
        alertRepository.clearKeywordReference(keyword.getId());
        keywordRepository.delete(keyword);
        List<Keyword> remaining = keywordRepository.findBySite(site);
        if (remaining.isEmpty()) {
            site.setDetectContentChange(true);
            siteRepository.save(site);
        }
    }

}
