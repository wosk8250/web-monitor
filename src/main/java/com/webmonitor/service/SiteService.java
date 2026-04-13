package com.webmonitor.service;

import com.webmonitor.domain.Site;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 사이트 관련 비즈니스 로직을 처리하는 서비스
 */
@Service // Spring의 Service 컴포넌트로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
@Transactional(readOnly = true) // 기본적으로 읽기 전용 트랜잭션 설정 (성능 최적화)
public class SiteService {

    private final SiteRepository siteRepository;

    /**
     * 새로운 사이트 등록
     * @param site 등록할 사이트 정보
     * @return 저장된 사이트
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    public Site createSite(Site site) {
        log.info("사이트 등록 시작: {}", site.getName());
        Site savedSite = siteRepository.save(site);
        log.info("사이트 등록 완료: ID = {}", savedSite.getId());
        return savedSite;
    }

    /**
     * 사이트 정보 수정
     * @param id 수정할 사이트 ID
     * @param updatedSite 수정할 사이트 정보
     * @return 수정된 사이트
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    public Site updateSite(Long id, Site updatedSite) {
        log.info("사이트 수정 시작: ID = {}", id);

        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + id));

        // 사이트 정보 업데이트
        site.setName(updatedSite.getName());
        site.setUrl(updatedSite.getUrl());
        site.setCheckInterval(updatedSite.getCheckInterval());
        site.setActive(updatedSite.getActive());

        Site saved = siteRepository.save(site);
        log.info("사이트 수정 완료: ID = {}", id);
        return saved;
    }

    /**
     * 사이트 삭제
     * @param id 삭제할 사이트 ID
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    public void deleteSite(Long id) {
        log.info("사이트 삭제 시작: ID = {}", id);

        if (!siteRepository.existsById(id)) {
            throw new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + id);
        }

        siteRepository.deleteById(id);
        log.info("사이트 삭제 완료: ID = {}", id);
    }

    /**
     * ID로 사이트 조회
     * @param id 조회할 사이트 ID
     * @return 조회된 사이트 (Optional)
     */
    public Optional<Site> getSiteById(Long id) {
        log.debug("사이트 조회: ID = {}", id);
        return siteRepository.findById(id);
    }

    /**
     * 모든 사이트 조회
     * @return 전체 사이트 목록
     */
    public List<Site> getAllSites() {
        log.debug("전체 사이트 조회");
        return siteRepository.findAll();
    }

    /**
     * 활성화된 사이트만 조회
     * @return 활성화된 사이트 목록
     */
    public List<Site> getActiveSites() {
        log.debug("활성화된 사이트 조회");
        return siteRepository.findByActive(true);
    }

    /**
     * 사이트 이름으로 검색
     * @param name 검색할 사이트 이름
     * @return 검색된 사이트 목록
     */
    public List<Site> searchSitesByName(String name) {
        log.debug("사이트 이름 검색: {}", name);
        return siteRepository.findByNameContaining(name);
    }

    /**
     * 사이트 활성화/비활성화 토글
     * @param id 토글할 사이트 ID
     * @return 변경된 사이트
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    public Site toggleSiteActive(Long id) {
        log.info("사이트 활성화 상태 변경: ID = {}", id);

        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + id));

        site.setActive(!site.getActive());
        Site saved = siteRepository.save(site);

        log.info("사이트 활성화 상태 변경 완료: ID = {}, Active = {}", id, saved.getActive());
        return saved;
    }
}
