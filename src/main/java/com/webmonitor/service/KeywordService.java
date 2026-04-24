package com.webmonitor.service;

import com.webmonitor.domain.Keyword;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.KeywordRequest;
import com.webmonitor.repository.KeywordRepository;
import com.webmonitor.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 키워드 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final SiteRepository siteRepository;

    /**
     * 새로운 키워드 등록
     * @param request 키워드 등록 요청 DTO
     * @return 생성된 키워드
     */
    @Transactional
    public Keyword createKeyword(KeywordRequest request) {
        log.info("키워드 등록 시작: keyword={}, siteId={}", request.getKeyword(), request.getSiteId());

        // 키워드 검증 (null은 불가)
        if (request.getKeyword() == null) {
            throw new IllegalArgumentException("키워드는 null일 수 없습니다");
        }

        // 공백 키워드 처리 → 새글 감지 활성화
        if (request.getKeyword().trim().isEmpty()) {
            log.info("공백 키워드 감지 - 새글 감지 기능 활성화");

            // 공백 키워드는 특정 사이트에만 허용
            if (request.getSiteId() == null) {
                throw new IllegalArgumentException("공백 키워드(새글 감지)는 특정 사이트에만 설정할 수 있습니다");
            }

            Site site = siteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + request.getSiteId()));

            // 새글 감지 활성화
            site.setDetectContentChange(true);
            siteRepository.save(site);
            log.info("사이트 '{}' 새글 감지 활성화 완료", site.getName());

            // 키워드는 저장하지 않음 (null 반환)
            return null;
        }

        // 일반 키워드 처리
        Site site = null;
        if (request.getSiteId() != null) {
            site = siteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> {
                        log.error("사이트를 찾을 수 없습니다: ID = {}", request.getSiteId());
                        return new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + request.getSiteId());
                    });
            log.debug("사이트 조회 완료: {}", site.getName());
        } else {
            log.debug("전체 공통 키워드로 등록합니다");
        }

        // Keyword 엔티티 생성
        Keyword keyword = Keyword.builder()
                .keyword(request.getKeyword())
                .site(site)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        // 저장
        Keyword savedKeyword = keywordRepository.save(keyword);
        log.info("키워드 등록 완료: ID = {}, keyword = {}, site = {}",
                savedKeyword.getId(),
                savedKeyword.getKeyword(),
                site != null ? site.getName() : "전체 공통");

        return savedKeyword;
    }

    /**
     * 키워드 정보 수정
     * @param id 수정할 키워드 ID
     * @param request 수정할 키워드 정보
     * @return 수정된 키워드
     */
    @Transactional
    public Keyword updateKeyword(Long id, KeywordRequest request) {
        log.info("키워드 수정 시작: ID = {}", id);

        Keyword keyword = keywordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("키워드를 찾을 수 없습니다. ID: " + id));

        // siteId가 있으면 Site 엔티티 조회
        Site site = null;
        if (request.getSiteId() != null) {
            site = siteRepository.findById(request.getSiteId())
                    .orElseThrow(() -> new IllegalArgumentException("사이트를 찾을 수 없습니다. ID: " + request.getSiteId()));
        }

        // 키워드 정보 업데이트
        keyword.setKeyword(request.getKeyword());
        keyword.setSite(site);
        keyword.setActive(request.getActive() != null ? request.getActive() : true);

        Keyword saved = keywordRepository.save(keyword);
        log.info("키워드 수정 완료: ID = {}", id);
        return saved;
    }

    /**
     * 키워드 삭제
     * @param id 삭제할 키워드 ID
     */
    @Transactional
    public void deleteKeyword(Long id) {
        log.info("키워드 삭제 시작: ID = {}", id);

        if (!keywordRepository.existsById(id)) {
            throw new IllegalArgumentException("키워드를 찾을 수 없습니다. ID: " + id);
        }

        keywordRepository.deleteById(id);
        log.info("키워드 삭제 완료: ID = {}", id);
    }

    /**
     * ID로 키워드 조회
     * @param id 조회할 키워드 ID
     * @return 조회된 키워드 (Optional)
     */
    public Optional<Keyword> getKeywordById(Long id) {
        log.debug("키워드 조회: ID = {}", id);
        return keywordRepository.findById(id);
    }

    /**
     * 모든 키워드 조회
     * @return 전체 키워드 목록
     */
    public List<Keyword> getAllKeywords() {
        log.debug("전체 키워드 조회");
        return keywordRepository.findAll();
    }

    /**
     * 활성화된 키워드만 조회
     * @return 활성화된 키워드 목록
     */
    public List<Keyword> getActiveKeywords() {
        log.debug("활성화된 키워드 조회");
        return keywordRepository.findByActive(true);
    }

    /**
     * 특정 사이트의 키워드 조회
     * @param siteId 사이트 ID
     * @return 해당 사이트의 키워드 목록
     */
    public List<Keyword> getKeywordsBySite(Long siteId) {
        log.debug("사이트별 키워드 조회: siteId = {}", siteId);
        return keywordRepository.findAll().stream()
                .filter(keyword -> keyword.getSite() != null && keyword.getSite().getId().equals(siteId))
                .toList();
    }

    /**
     * 전체 공통 키워드 조회
     * @return 전체 공통 키워드 목록
     */
    public List<Keyword> getGlobalKeywords() {
        log.debug("전체 공통 키워드 조회");
        return keywordRepository.findAll().stream()
                .filter(keyword -> keyword.getSite() == null)
                .toList();
    }

    /**
     * 키워드 활성화/비활성화 토글
     * @param id 토글할 키워드 ID
     * @return 변경된 키워드
     */
    @Transactional
    public Keyword toggleKeywordActive(Long id) {
        log.info("키워드 활성화 상태 변경: ID = {}", id);

        Keyword keyword = keywordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("키워드를 찾을 수 없습니다. ID: " + id));

        keyword.setActive(!keyword.getActive());
        Keyword saved = keywordRepository.save(keyword);

        log.info("키워드 활성화 상태 변경 완료: ID = {}, Active = {}", id, saved.getActive());
        return saved;
    }
}
