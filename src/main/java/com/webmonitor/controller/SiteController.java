package com.webmonitor.controller;

import com.webmonitor.dto.SiteRequest;
import com.webmonitor.dto.SiteResponse;
import com.webmonitor.service.SiteService;
import com.webmonitor.util.XssSanitizer;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사이트 관리 REST API 컨트롤러
 * Rate Limiter 적용: API 과부하 방지 (10초당 최대 100 요청)
 */
@RestController // REST API 컨트롤러로 지정 (@Controller + @ResponseBody)
@RequestMapping("/api/sites") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
@RateLimiter(name = "api")
public class SiteController {

    private final SiteService siteService;

    /**
     * 모든 사이트 조회
     * GET /api/sites
     * @return 전체 사이트 DTO 목록
     */
    @GetMapping
    public ResponseEntity<List<SiteResponse>> getAllSites() {
        log.info("GET /api/sites - 모든 사이트 조회 요청");
        List<SiteResponse> sites = siteService.getAllSites();
        return ResponseEntity.ok(sites);
    }

    /**
     * 활성화된 사이트만 조회
     * GET /api/sites/active
     * @return 활성화된 사이트 DTO 목록
     */
    @GetMapping("/active")
    public ResponseEntity<List<SiteResponse>> getActiveSites() {
        log.info("GET /api/sites/active - 활성화된 사이트 조회 요청");
        List<SiteResponse> sites = siteService.getActiveSites();
        return ResponseEntity.ok(sites);
    }

    /**
     * ID로 특정 사이트 조회
     * GET /api/sites/{id}
     * @param id 사이트 ID
     * @return 조회된 사이트 DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<SiteResponse> getSiteById(@PathVariable Long id) {
        log.info("GET /api/sites/{} - 사이트 조회 요청", id);
        return siteService.getSiteById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 사이트 이름으로 검색
     * GET /api/sites/search?name={name}
     * XSS 방어: 검색어를 HTML 이스케이프 처리
     * @param name 검색할 사이트 이름
     * @return 검색된 사이트 DTO 목록
     */
    @GetMapping("/search")
    public ResponseEntity<List<SiteResponse>> searchSites(@RequestParam String name) {
        String sanitizedName = XssSanitizer.sanitize(name);
        log.info("GET /api/sites/search?name={} - 사이트 검색 요청", sanitizedName);
        List<SiteResponse> sites = siteService.searchSitesByName(sanitizedName);
        return ResponseEntity.ok(sites);
    }

    /**
     * 새로운 사이트 등록
     * POST /api/sites
     * @param request 등록할 사이트 정보 DTO (JSON)
     * @return 생성된 사이트 DTO
     */
    @PostMapping
    public ResponseEntity<SiteResponse> createSite(@Valid @RequestBody SiteRequest request) {
        log.info("POST /api/sites - 사이트 등록 요청: {}", request.getName());
        try {
            SiteResponse createdSite = siteService.createSite(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdSite);
        } catch (Exception e) {
            log.error("사이트 등록 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 사이트 정보 수정
     * PUT /api/sites/{id}
     * @param id 수정할 사이트 ID
     * @param request 수정할 사이트 정보 DTO (JSON)
     * @return 수정된 사이트 DTO
     */
    @PutMapping("/{id}")
    public ResponseEntity<SiteResponse> updateSite(@PathVariable Long id, @Valid @RequestBody SiteRequest request) {
        log.info("PUT /api/sites/{} - 사이트 수정 요청", id);
        try {
            SiteResponse updatedSite = siteService.updateSite(id, request);
            return ResponseEntity.ok(updatedSite);
        } catch (IllegalArgumentException e) {
            log.error("사이트 수정 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("사이트 수정 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 사이트 삭제
     * DELETE /api/sites/{id}
     * @param id 삭제할 사이트 ID
     * @return 삭제 성공 여부
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSite(@PathVariable Long id) {
        log.info("DELETE /api/sites/{} - 사이트 삭제 요청", id);
        try {
            siteService.deleteSite(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("사이트 삭제 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("사이트 삭제 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 사이트 활성화/비활성화 토글
     * PATCH /api/sites/{id}/toggle
     * @param id 토글할 사이트 ID
     * @return 변경된 사이트 DTO
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<SiteResponse> toggleSiteActive(@PathVariable Long id) {
        log.info("PATCH /api/sites/{}/toggle - 사이트 활성화 상태 변경 요청", id);
        try {
            SiteResponse toggledSite = siteService.toggleSiteActive(id);
            return ResponseEntity.ok(toggledSite);
        } catch (IllegalArgumentException e) {
            log.error("사이트 활성화 토글 실패: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("사이트 활성화 토글 중 오류 발생: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
