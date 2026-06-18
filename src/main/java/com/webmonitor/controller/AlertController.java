package com.webmonitor.controller;

import com.webmonitor.dto.AlertBulkPatchRequest;
import com.webmonitor.dto.AlertPatchRequest;
import com.webmonitor.dto.AlertResponse;
import com.webmonitor.service.AlertService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
@Slf4j
@RateLimiter(name = "api")
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAlerts(
            @RequestParam(required = false) Boolean sent,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        if (from != null || to != null) {
            if (from == null || to == null) {
                return ResponseEntity.badRequest().build();
            }
            log.info("GET /api/alerts?from={}&to={} - 기간별 알림 조회", from, to);
            return ResponseEntity.ok(alertService.getAlertResponsesByPeriod(from, to));
        }
        if (sent != null) {
            log.info("GET /api/alerts?sent={} - 알림 필터 조회", sent);
            return ResponseEntity.ok(sent
                    ? alertService.getSentAlertResponses()
                    : alertService.getUnsentAlertResponses());
        }
        log.info("GET /api/alerts - 모든 알림 조회 요청");
        return ResponseEntity.ok(alertService.getAllAlertResponses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlertById(@PathVariable Long id) {
        log.info("GET /api/alerts/{} - 알림 조회 요청", id);
        return ResponseEntity.ok(alertService.getAlertResponseById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AlertResponse> patchAlert(@PathVariable Long id, @RequestBody AlertPatchRequest request) {
        log.info("PATCH /api/alerts/{} - 알림 수정 요청", id);
        if (Boolean.TRUE.equals(request.getSent())) {
            return ResponseEntity.ok(alertService.markAsSentResponse(id));
        }
        return ResponseEntity.badRequest().build();
    }

    @PatchMapping
    public ResponseEntity<String> patchAlertsBulk(@RequestBody(required = false) AlertBulkPatchRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return ResponseEntity.ok("0건의 알림이 처리되었습니다.");
        }
        if (Boolean.TRUE.equals(request.getSent())) {
            log.info("PATCH /api/alerts - 알림 일괄 읽음 처리 요청: {} 건", request.getIds().size());
            int updatedCount = alertService.markAsSentBulk(request.getIds());
            return ResponseEntity.ok(updatedCount + "건의 알림이 읽음 처리되었습니다.");
        }
        return ResponseEntity.badRequest().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        log.info("DELETE /api/alerts/{} - 알림 삭제 요청", id);
        alertService.deleteAlert(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<String> deleteAlerts(@RequestParam(required = false) Boolean sent) {
        if (Boolean.TRUE.equals(sent)) {
            log.info("DELETE /api/alerts?sent=true - 읽은 알림 전체 삭제 요청");
            int deletedCount = alertService.deleteSentAlerts();
            return ResponseEntity.ok(deletedCount + "건의 알림이 삭제되었습니다.");
        }
        if (sent != null) {
            return ResponseEntity.badRequest().build();
        }
        log.info("DELETE /api/alerts - 모든 알림 삭제 요청");
        int deletedCount = alertService.deleteAllAlerts();
        return ResponseEntity.ok(deletedCount + "건의 알림이 삭제되었습니다.");
    }
}
