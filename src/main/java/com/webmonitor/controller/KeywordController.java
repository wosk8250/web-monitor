package com.webmonitor.controller;

import com.webmonitor.dto.AlertResponse;
import com.webmonitor.dto.KeywordRequest;
import com.webmonitor.dto.KeywordResponse;
import com.webmonitor.service.AlertService;
import com.webmonitor.service.KeywordService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/keywords")
@RequiredArgsConstructor
@Slf4j
@RateLimiter(name = "api")
public class KeywordController {

    private final KeywordService keywordService;
    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<KeywordResponse>> getKeywords(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Boolean global) {
        if (global != null && active != null) {
            return ResponseEntity.badRequest().build();
        }
        if (Boolean.FALSE.equals(global)) {
            return ResponseEntity.badRequest().build();
        }
        if (Boolean.TRUE.equals(global)) {
            log.info("GET /api/keywords?global=true - 전체 공통 키워드 조회 요청");
            return ResponseEntity.ok(keywordService.getGlobalKeywords());
        }
        if (active != null) {
            log.info("GET /api/keywords?active={} - 키워드 필터 조회 요청", active);
            return ResponseEntity.ok(keywordService.getKeywordsByActive(active));
        }
        log.info("GET /api/keywords - 모든 키워드 조회 요청");
        return ResponseEntity.ok(keywordService.getAllKeywords());
    }

    @GetMapping("/{id}")
    public ResponseEntity<KeywordResponse> getKeywordById(@PathVariable Long id) {
        log.info("GET /api/keywords/{} - 키워드 조회 요청", id);
        return keywordService.getKeywordById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createKeyword(@Valid @RequestBody KeywordRequest request) {
        log.info("POST /api/keywords - 키워드 등록 요청: keyword={}, siteId={}",
                request.getKeyword(), request.getSiteId());

        return keywordService.createKeyword(request)
                .<ResponseEntity<?>>map(dto -> ResponseEntity.status(HttpStatus.CREATED).body(dto))
                .orElseGet(() -> ResponseEntity.ok(Map.of("message", "새글 감지 기능이 활성화되었습니다")));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<KeywordResponse> updateKeyword(
            @PathVariable Long id,
            @RequestBody KeywordRequest request) {
        log.info("PATCH /api/keywords/{} - 키워드 수정 요청", id);
        return ResponseEntity.ok(keywordService.updateKeyword(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteKeyword(@PathVariable Long id) {
        log.info("DELETE /api/keywords/{} - 키워드 삭제 요청", id);
        keywordService.deleteKeyword(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{keywordId}/alerts")
    public ResponseEntity<List<AlertResponse>> getAlertsByKeyword(@PathVariable Long keywordId) {
        log.info("GET /api/keywords/{}/alerts - 키워드별 알림 조회 요청", keywordId);
        if (keywordService.getKeywordById(keywordId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(alertService.getAlertResponsesByKeyword(keywordId));
    }
}
