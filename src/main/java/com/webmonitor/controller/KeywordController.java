package com.webmonitor.controller;

import com.webmonitor.domain.Keyword;
import com.webmonitor.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 키워드 관리 REST API 컨트롤러
 */
@RestController // REST API 컨트롤러로 지정 (@Controller + @ResponseBody)
@RequestMapping("/api/keywords") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class KeywordController {

    private final KeywordRepository keywordRepository;

    /**
     * 모든 키워드 조회
     * GET /api/keywords
     * @return 전체 키워드 목록
     */
    @GetMapping
    public ResponseEntity<List<Keyword>> getAllKeywords() {
        log.info("GET /api/keywords - 모든 키워드 조회 요청");
        List<Keyword> keywords = keywordRepository.findAll();
        return ResponseEntity.ok(keywords);
    }

    /**
     * ID로 특정 키워드 조회
     * GET /api/keywords/{id}
     * @param id 키워드 ID
     * @return 조회된 키워드
     */
    @GetMapping("/{id}")
    public ResponseEntity<Keyword> getKeywordById(@PathVariable Long id) {
        log.info("GET /api/keywords/{} - 키워드 조회 요청", id);
        return keywordRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 활성화된 키워드만 조회
     * GET /api/keywords/active
     * @return 활성화된 키워드 목록
     */
    @GetMapping("/active")
    public ResponseEntity<List<Keyword>> getActiveKeywords() {
        log.info("GET /api/keywords/active - 활성화된 키워드 조회 요청");
        List<Keyword> keywords = keywordRepository.findByActive(true);
        return ResponseEntity.ok(keywords);
    }

    /**
     * 특정 사이트의 키워드 조회
     * GET /api/keywords/site/{siteId}
     * @param siteId 사이트 ID
     * @return 해당 사이트의 키워드 목록
     */
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<Keyword>> getKeywordsBySite(@PathVariable Long siteId) {
        log.info("GET /api/keywords/site/{} - 사이트별 키워드 조회 요청", siteId);
        List<Keyword> keywords = keywordRepository.findAll().stream()
                .filter(keyword -> keyword.getSite() != null && keyword.getSite().getId().equals(siteId))
                .toList();
        return ResponseEntity.ok(keywords);
    }

    /**
     * 전체 공통 키워드 조회 (사이트가 지정되지 않은 키워드)
     * GET /api/keywords/global
     * @return 전체 공통 키워드 목록
     */
    @GetMapping("/global")
    public ResponseEntity<List<Keyword>> getGlobalKeywords() {
        log.info("GET /api/keywords/global - 전체 공통 키워드 조회 요청");
        List<Keyword> keywords = keywordRepository.findAll().stream()
                .filter(keyword -> keyword.getSite() == null)
                .toList();
        return ResponseEntity.ok(keywords);
    }

    /**
     * 새로운 키워드 등록
     * POST /api/keywords
     * @param keyword 등록할 키워드 정보 (JSON)
     * @return 생성된 키워드 정보
     */
    @PostMapping
    public ResponseEntity<Keyword> createKeyword(@RequestBody Keyword keyword) {
        log.info("POST /api/keywords - 키워드 등록 요청: {}", keyword.getKeyword());
        try {
            Keyword createdKeyword = keywordRepository.save(keyword);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdKeyword);
        } catch (Exception e) {
            log.error("키워드 등록 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 키워드 정보 수정
     * PUT /api/keywords/{id}
     * @param id 수정할 키워드 ID
     * @param updatedKeyword 수정할 키워드 정보 (JSON)
     * @return 수정된 키워드 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<Keyword> updateKeyword(@PathVariable Long id, @RequestBody Keyword updatedKeyword) {
        log.info("PUT /api/keywords/{} - 키워드 수정 요청", id);
        try {
            return keywordRepository.findById(id)
                    .map(keyword -> {
                        keyword.setKeyword(updatedKeyword.getKeyword());
                        keyword.setActive(updatedKeyword.getActive());
                        keyword.setSite(updatedKeyword.getSite());
                        Keyword saved = keywordRepository.save(keyword);
                        return ResponseEntity.ok(saved);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("키워드 수정 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 키워드 삭제
     * DELETE /api/keywords/{id}
     * @param id 삭제할 키워드 ID
     * @return 삭제 성공 여부
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeyword(@PathVariable Long id) {
        log.info("DELETE /api/keywords/{} - 키워드 삭제 요청", id);
        try {
            if (!keywordRepository.existsById(id)) {
                log.error("키워드를 찾을 수 없습니다: ID = {}", id);
                return ResponseEntity.notFound().build();
            }

            keywordRepository.deleteById(id);
            log.info("키워드 삭제 완료: ID = {}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("키워드 삭제 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 키워드 활성화/비활성화 토글
     * PATCH /api/keywords/{id}/toggle
     * @param id 토글할 키워드 ID
     * @return 변경된 키워드 정보
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<Keyword> toggleKeywordActive(@PathVariable Long id) {
        log.info("PATCH /api/keywords/{}/toggle - 키워드 활성화 상태 변경 요청", id);
        try {
            return keywordRepository.findById(id)
                    .map(keyword -> {
                        keyword.setActive(!keyword.getActive());
                        Keyword saved = keywordRepository.save(keyword);
                        log.info("키워드 활성화 상태 변경 완료: ID = {}, Active = {}", id, saved.getActive());
                        return ResponseEntity.ok(saved);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("키워드 활성화 토글 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
