package com.webmonitor.controller;

import com.webmonitor.domain.Keyword;
import com.webmonitor.dto.KeywordRequest;
import com.webmonitor.dto.KeywordResponse;
import com.webmonitor.service.KeywordService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 키워드 관리 REST API 컨트롤러
 * Rate Limiter 적용: API 과부하 방지 (10초당 최대 100 요청)
 */
@RestController
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
@Slf4j
@RateLimiter(name = "api")
public class KeywordController {

    private final KeywordService keywordService;

    /**
     * 모든 키워드 조회
     * GET /api/keywords
     * @return 전체 키워드 목록
     */
    @GetMapping
    public ResponseEntity<List<KeywordResponse>> getAllKeywords() {
        log.info("GET /api/keywords - 모든 키워드 조회 요청");
        List<KeywordResponse> keywords = keywordService.getAllKeywords().stream()
                .map(KeywordResponse::from)
                .toList();
        return ResponseEntity.ok(keywords);
    }

    /**
     * ID로 특정 키워드 조회
     * GET /api/keywords/{id}
     * @param id 키워드 ID
     * @return 조회된 키워드
     */
    @GetMapping("/{id}")
    public ResponseEntity<KeywordResponse> getKeywordById(@PathVariable Long id) {
        log.info("GET /api/keywords/{} - 키워드 조회 요청", id);
        return keywordService.getKeywordById(id)
                .map(KeywordResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 활성화된 키워드만 조회
     * GET /api/keywords/active
     * @return 활성화된 키워드 목록
     */
    @GetMapping("/active")
    public ResponseEntity<List<KeywordResponse>> getActiveKeywords() {
        log.info("GET /api/keywords/active - 활성화된 키워드 조회 요청");
        List<KeywordResponse> keywords = keywordService.getActiveKeywords().stream()
                .map(KeywordResponse::from)
                .toList();
        return ResponseEntity.ok(keywords);
    }

    /**
     * 특정 사이트의 키워드 조회
     * GET /api/keywords/site/{siteId}
     * @param siteId 사이트 ID
     * @return 해당 사이트의 키워드 목록
     */
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<KeywordResponse>> getKeywordsBySite(@PathVariable Long siteId) {
        log.info("GET /api/keywords/site/{} - 사이트별 키워드 조회 요청", siteId);
        List<KeywordResponse> keywords = keywordService.getKeywordsBySite(siteId).stream()
                .map(KeywordResponse::from)
                .toList();
        return ResponseEntity.ok(keywords);
    }

    /**
     * 전체 공통 키워드 조회 (사이트가 지정되지 않은 키워드)
     * GET /api/keywords/global
     * @return 전체 공통 키워드 목록
     */
    @GetMapping("/global")
    public ResponseEntity<List<KeywordResponse>> getGlobalKeywords() {
        log.info("GET /api/keywords/global - 전체 공통 키워드 조회 요청");
        List<KeywordResponse> keywords = keywordService.getGlobalKeywords().stream()
                .map(KeywordResponse::from)
                .toList();
        return ResponseEntity.ok(keywords);
    }

    /**
     * 새로운 키워드 등록
     * POST /api/keywords
     * @param request 등록할 키워드 정보 (JSON)
     * @return 생성된 키워드 정보
     */
    @PostMapping
    public ResponseEntity<?> createKeyword(@Valid @RequestBody KeywordRequest request) {
        log.info("POST /api/keywords - 키워드 등록 요청: keyword={}, siteId={}",
                request.getKeyword(), request.getSiteId());
        try {
            Keyword createdKeyword = keywordService.createKeyword(request);

            // 공백 키워드 = 새글 감지 활성화 (키워드 저장 안함)
            if (createdKeyword == null) {
                return ResponseEntity.ok().body(new java.util.HashMap<String, String>() {{
                    put("message", "새글 감지 기능이 활성화되었습니다");
                }});
            }

            // 일반 키워드 저장
            return ResponseEntity.status(HttpStatus.CREATED).body(KeywordResponse.from(createdKeyword));
        } catch (IllegalArgumentException e) {
            log.error("키워드 등록 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new java.util.HashMap<String, String>() {{
                        put("error", e.getMessage());
                    }});
        } catch (Exception e) {
            log.error("키워드 등록 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 키워드 정보 수정
     * PUT /api/keywords/{id}
     * @param id 수정할 키워드 ID
     * @param request 수정할 키워드 정보 (JSON)
     * @return 수정된 키워드 정보
     */
    @PutMapping("/{id}")
    public ResponseEntity<KeywordResponse> updateKeyword(
            @PathVariable Long id,
            @Valid @RequestBody KeywordRequest request) {
        log.info("PUT /api/keywords/{} - 키워드 수정 요청", id);
        try {
            Keyword updatedKeyword = keywordService.updateKeyword(id, request);
            return ResponseEntity.ok(KeywordResponse.from(updatedKeyword));
        } catch (IllegalArgumentException e) {
            log.error("키워드 수정 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("키워드 수정 중 오류 발생: {}", e.getMessage(), e);
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
            keywordService.deleteKeyword(id);
            log.info("키워드 삭제 완료: ID = {}", id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("키워드 삭제 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("키워드 삭제 중 오류 발생: {}", e.getMessage(), e);
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
    public ResponseEntity<KeywordResponse> toggleKeywordActive(@PathVariable Long id) {
        log.info("PATCH /api/keywords/{}/toggle - 키워드 활성화 상태 변경 요청", id);
        try {
            Keyword toggledKeyword = keywordService.toggleKeywordActive(id);
            return ResponseEntity.ok(KeywordResponse.from(toggledKeyword));
        } catch (IllegalArgumentException e) {
            log.error("키워드 토글 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("키워드 활성화 토글 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
