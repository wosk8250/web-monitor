package com.webmonitor.controller;

import com.webmonitor.dto.SettingRequest;
import com.webmonitor.dto.SettingResponse;
import com.webmonitor.service.SettingService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Slf4j
@RateLimiter(name = "api")
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public ResponseEntity<List<SettingResponse>> getSettings(
            @RequestParam(required = false) Boolean enabled) {
        if (enabled != null) {
            log.info("GET /api/settings?enabled={} - 설정 필터 조회 요청", enabled);
            return ResponseEntity.ok(settingService.getSettingsByEnabled(enabled));
        }
        log.info("GET /api/settings - 모든 설정 조회 요청");
        return ResponseEntity.ok(settingService.getAllSettings());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettingResponse> getSettingById(@PathVariable Long id) {
        log.info("GET /api/settings/{} - 설정 조회 요청", id);
        return ResponseEntity.ok(settingService.getSettingById(id));
    }

    @PostMapping
    public ResponseEntity<SettingResponse> createSetting(@RequestBody @Valid SettingRequest request) {
        log.info("POST /api/settings - 설정 등록 요청");
        return ResponseEntity.status(HttpStatus.CREATED).body(settingService.createSetting(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<SettingResponse> updateSetting(@PathVariable Long id, @RequestBody @Valid SettingRequest request) {
        log.info("PATCH /api/settings/{} - 설정 수정 요청", id);
        return ResponseEntity.ok(settingService.updateSetting(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSetting(@PathVariable Long id) {
        log.info("DELETE /api/settings/{} - 설정 삭제 요청", id);
        settingService.deleteSetting(id);
        return ResponseEntity.noContent().build();
    }
}
