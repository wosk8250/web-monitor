package com.webmonitor.service;

import com.webmonitor.domain.Site;
import com.webmonitor.dto.SiteRequest;
import com.webmonitor.dto.SiteResponse;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.repository.ArticleRepository;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 사이트 관련 비즈니스 로직을 처리하는 서비스
 */
@Service // Spring의 Service 컴포넌트로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션 설정 (성능 최적화)
public class SiteService {

    private final SiteRepository siteRepository;
    private final AlertRepository alertRepository;
    private final KeywordRepository keywordRepository;
    private final ArticleRepository articleRepository;

    /**
     * 새로운 사이트 등록
     * @param request 등록할 사이트 정보 (DTO)
     * @return 저장된 사이트 (DTO)
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    public SiteResponse createSite(SiteRequest request) {
        log.info("사이트 등록 시작: {}", request.getName());

        // DTO를 Entity로 변환
        Site site = Site.builder()
                .name(request.getName())
                .url(request.getUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Site savedSite = siteRepository.save(site);
        log.info("사이트 등록 완료: ID = {}", savedSite.getId());

        // Entity를 DTO로 변환하여 반환
        return SiteResponse.from(savedSite);
    }

    /**
     * 사이트 정보 수정
     * @param id 수정할 사이트 ID
     * @param request 수정할 사이트 정보 (DTO)
     * @return 수정된 사이트 (DTO)
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    @CacheEvict(value = {"sites", "allSites", "activeSites"}, allEntries = true) // 전체 캐시 무효화
    public SiteResponse updateSite(Long id, SiteRequest request) {
        log.info("사이트 수정 시작: ID = {}", id);

        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + id));

        // 사이트 정보 업데이트
        site.setName(request.getName());
        site.setUrl(request.getUrl());
        if (request.getActive() != null) {
            site.setActive(request.getActive());
        }

        Site saved = siteRepository.save(site);
        log.info("사이트 수정 완료: ID = {}", id);

        // Entity를 DTO로 변환하여 반환
        return SiteResponse.from(saved);
    }

    /**
     * 사이트 삭제
     * @param id 삭제할 사이트 ID
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    @CacheEvict(value = {"sites", "allSites", "activeSites"}, allEntries = true) // 전체 캐시 무효화
    public void deleteSite(Long id) {
        log.info("사이트 삭제 시작: ID = {}", id);

        // 사이트 존재 여부 확인
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + id));

        // 외래 키 제약 조건 때문에 연관된 엔티티를 먼저 삭제
        // 1. 해당 사이트의 모든 알림 삭제
        alertRepository.deleteBySiteId(id);
        log.debug("사이트 ID {}의 모든 알림 삭제 완료", id);

        // 2. 해당 사이트의 모든 키워드 삭제
        keywordRepository.deleteBySiteId(id);
        log.debug("사이트 ID {}의 모든 키워드 삭제 완료", id);

        // 3. 해당 사이트의 모든 게시글 삭제
        articleRepository.deleteBySite(site);
        log.debug("사이트 ID {}의 모든 게시글 삭제 완료", id);

        // 4. 사이트 삭제
        siteRepository.delete(site);
        log.info("사이트 삭제 완료: ID = {}", id);
    }

    /**
     * ID로 사이트 조회 (캐시 적용)
     * @param id 조회할 사이트 ID
     * @return 조회된 사이트 DTO (Optional)
     */
    @Cacheable(value = "sites", key = "#id") // 캐시 저장
    public Optional<SiteResponse> getSiteById(Long id) {
        log.debug("사이트 조회 (DB): ID = {}", id);
        return siteRepository.findById(id)
                .map(SiteResponse::from);
    }

    /**
     * 모든 사이트 조회 (캐시 적용)
     * @return 전체 사이트 DTO 목록
     */
    @Cacheable(value = "allSites")
    public List<SiteResponse> getAllSites() {
        log.debug("전체 사이트 조회 (DB)");
        return siteRepository.findAll().stream()
                .map(SiteResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 활성화된 사이트만 조회 (캐시 적용)
     * @return 활성화된 사이트 DTO 목록
     */
    @Cacheable(value = "activeSites")
    public List<SiteResponse> getActiveSites() {
        log.debug("활성화된 사이트 조회 (DB)");
        return siteRepository.findByActive(true).stream()
                .map(SiteResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사이트 이름으로 검색
     * @param name 검색할 사이트 이름
     * @return 검색된 사이트 DTO 목록
     */
    public List<SiteResponse> searchSitesByName(String name) {
        log.debug("사이트 이름 검색: {}", name);
        return siteRepository.findByNameContaining(name).stream()
                .map(SiteResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사이트 활성화/비활성화 토글
     * @param id 토글할 사이트 ID
     * @return 변경된 사이트 DTO
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    @CacheEvict(value = {"sites", "allSites", "activeSites"}, allEntries = true) // 전체 캐시 무효화
    public SiteResponse toggleSiteActive(Long id) {
        log.info("사이트 활성화 상태 변경: ID = {}", id);

        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + id));

        site.setActive(!site.getActive());
        Site saved = siteRepository.save(site);

        log.info("사이트 활성화 상태 변경 완료: ID = {}, Active = {}", id, saved.getActive());

        // Entity를 DTO로 변환하여 반환
        return SiteResponse.from(saved);
    }
}
