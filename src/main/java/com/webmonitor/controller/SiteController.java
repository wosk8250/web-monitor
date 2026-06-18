package com.webmonitor.controller;

import com.webmonitor.dto.AlertResponse;
import com.webmonitor.dto.KeywordResponse;
import com.webmonitor.dto.SiteRequest;
import com.webmonitor.dto.SiteResponse;
import com.webmonitor.service.AlertService;
import com.webmonitor.service.KeywordService;
import com.webmonitor.service.SiteService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
@Slf4j
@RateLimiter(name = "api")
public class SiteController {

    private final SiteService siteService;
    private final AlertService alertService;
    private final KeywordService keywordService;

    @GetMapping
    public ResponseEntity<List<SiteResponse>> getSites(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String name) {
        if (name != null && active != null) {
            return ResponseEntity.badRequest().build();
        }
        if (name != null) {
            log.info("GET /api/sites?name={} - 사이트 검색 요청", name);
            return ResponseEntity.ok(siteService.searchSitesByName(name));
        }
        if (active != null) {
            log.info("GET /api/sites?active={} - 사이트 필터 조회 요청", active);
            return ResponseEntity.ok(siteService.getSitesByActive(active));
        }
        log.info("GET /api/sites - 모든 사이트 조회 요청");
        return ResponseEntity.ok(siteService.getAllSites());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SiteResponse> getSiteById(@PathVariable Long id) {
        log.info("GET /api/sites/{} - 사이트 조회 요청", id);
        return siteService.getSiteById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SiteResponse> createSite(@Valid @RequestBody SiteRequest request) {
        log.info("POST /api/sites - 사이트 등록 요청: {}", request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.createSite(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SiteResponse> updateSite(@PathVariable Long id, @RequestBody SiteRequest request) {
        log.info("PATCH /api/sites/{} - 사이트 수정 요청", id);
        return ResponseEntity.ok(siteService.updateSite(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSite(@PathVariable Long id) {
        log.info("DELETE /api/sites/{} - 사이트 삭제 요청", id);
        siteService.deleteSite(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{siteId}/alerts")
    public ResponseEntity<List<AlertResponse>> getAlertsBySite(@PathVariable Long siteId) {
        log.info("GET /api/sites/{}/alerts - 사이트별 알림 조회 요청", siteId);
        if (siteService.getSiteById(siteId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alertService.getAlertResponsesBySite(siteId));
    }

    @GetMapping("/{siteId}/keywords")
    public ResponseEntity<List<KeywordResponse>> getKeywordsBySite(@PathVariable Long siteId) {
        log.info("GET /api/sites/{}/keywords - 사이트별 키워드 조회 요청", siteId);
        if (siteService.getSiteById(siteId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(keywordService.getKeywordsBySite(siteId));
    }
}
