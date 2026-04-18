package com.webmonitor.controller;

import com.webmonitor.domain.Alert;
import com.webmonitor.dto.AlertResponse;
import com.webmonitor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 알림 관리 REST API 컨트롤러
 */
@RestController // REST API 컨트롤러로 지정 (@Controller + @ResponseBody)
@RequestMapping("/api/alerts") // 기본 URL 경로 설정
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class AlertController {

    private final AlertRepository alertRepository;

    /**
     * 모든 알림 조회
     * GET /api/alerts
     * @return 전체 알림 목록 (최신순)
     */
    @GetMapping
    public ResponseEntity<List<AlertResponse>> getAllAlerts() {
        log.info("GET /api/alerts - 모든 알림 조회 요청");
        List<AlertResponse> alerts = alertRepository.findAll().stream()
                .map(AlertResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(alerts);
    }

    /**
     * ID로 특정 알림 조회
     * GET /api/alerts/{id}
     * @param id 알림 ID
     * @return 조회된 알림
     */
    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getAlertById(@PathVariable Long id) {
        log.info("GET /api/alerts/{} - 알림 조회 요청", id);
        return alertRepository.findById(id)
                .map(AlertResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 미전송 알림 조회
     * GET /api/alerts/unsent
     * @return 전송되지 않은 알림 목록
     */
    @GetMapping("/unsent")
    public ResponseEntity<List<AlertResponse>> getUnsentAlerts() {
        log.info("GET /api/alerts/unsent - 미전송 알림 조회 요청");
        List<AlertResponse> alerts = alertRepository.findBySent(false).stream()
                .map(AlertResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(alerts);
    }

    /**
     * 전송 완료된 알림 조회
     * GET /api/alerts/sent
     * @return 전송된 알림 목록
     */
    @GetMapping("/sent")
    public ResponseEntity<List<AlertResponse>> getSentAlerts() {
        log.info("GET /api/alerts/sent - 전송 완료 알림 조회 요청");
        List<AlertResponse> alerts = alertRepository.findBySent(true).stream()
                .map(AlertResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(alerts);
    }

    /**
     * 특정 사이트의 알림 조회
     * GET /api/alerts/site/{siteId}
     * @param siteId 사이트 ID
     * @return 해당 사이트의 알림 목록
     */
    @GetMapping("/site/{siteId}")
    public ResponseEntity<List<AlertResponse>> getAlertsBySite(@PathVariable Long siteId) {
        log.info("GET /api/alerts/site/{} - 사이트별 알림 조회 요청", siteId);
        List<AlertResponse> alerts = alertRepository.findAll().stream()
                .filter(alert -> alert.getSite().getId().equals(siteId))
                .map(AlertResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(alerts);
    }

    /**
     * 특정 키워드의 알림 조회
     * GET /api/alerts/keyword/{keywordId}
     * @param keywordId 키워드 ID
     * @return 해당 키워드의 알림 목록
     */
    @GetMapping("/keyword/{keywordId}")
    public ResponseEntity<List<AlertResponse>> getAlertsByKeyword(@PathVariable Long keywordId) {
        log.info("GET /api/alerts/keyword/{} - 키워드별 알림 조회 요청", keywordId);
        List<AlertResponse> alerts = alertRepository.findAll().stream()
                .filter(alert -> alert.getKeyword() != null && alert.getKeyword().getId().equals(keywordId))
                .map(AlertResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(alerts);
    }

    /**
     * 특정 기간의 알림 조회
     * GET /api/alerts/period?start={startDate}&end={endDate}
     * @param startDate 시작 일시 (ISO 형식: 2024-01-01T00:00:00)
     * @param endDate 종료 일시 (ISO 형식: 2024-12-31T23:59:59)
     * @return 해당 기간의 알림 목록
     */
    @GetMapping("/period")
    public ResponseEntity<List<AlertResponse>> getAlertsByPeriod(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        log.info("GET /api/alerts/period - 기간별 알림 조회: {} ~ {}", startDate, endDate);
        List<AlertResponse> alerts = alertRepository.findByDetectedAtBetween(startDate, endDate).stream()
                .map(AlertResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(alerts);
    }

    /**
     * 알림을 읽음(전송됨)으로 표시
     * PATCH /api/alerts/{id}/mark-sent
     * @param id 알림 ID
     * @return 업데이트된 알림 정보
     */
    @PatchMapping("/{id}/mark-sent")
    public ResponseEntity<AlertResponse> markAlertAsSent(@PathVariable Long id) {
        log.info("PATCH /api/alerts/{}/mark-sent - 알림 읽음 처리 요청", id);

        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setSent(true);
                    alert.setSentAt(LocalDateTime.now());
                    Alert updatedAlert = alertRepository.save(alert);
                    log.info("알림 읽음 처리 완료: ID = {}", id);
                    return ResponseEntity.ok(AlertResponse.from(updatedAlert));
                })
                .orElseGet(() -> {
                    log.error("알림을 찾을 수 없습니다: ID = {}", id);
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * 여러 알림을 읽음(전송됨)으로 일괄 처리
     * PATCH /api/alerts/mark-sent-bulk
     * @param ids 알림 ID 목록 (JSON 배열)
     * @return 처리 성공 여부
     */
    @PatchMapping("/mark-sent-bulk")
    public ResponseEntity<String> markAlertsAsSentBulk(@RequestBody List<Long> ids) {
        log.info("PATCH /api/alerts/mark-sent-bulk - 알림 일괄 읽음 처리 요청: {} 건", ids.size());

        try {
            int updatedCount = 0;
            for (Long id : ids) {
                alertRepository.findById(id).ifPresent(alert -> {
                    alert.setSent(true);
                    alert.setSentAt(LocalDateTime.now());
                    alertRepository.save(alert);
                });
                updatedCount++;
            }

            log.info("알림 일괄 읽음 처리 완료: {} 건", updatedCount);
            return ResponseEntity.ok(updatedCount + "건의 알림이 읽음 처리되었습니다.");
        } catch (Exception e) {
            log.error("알림 일괄 읽음 처리 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("알림 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 알림 삭제
     * DELETE /api/alerts/{id}
     * @param id 삭제할 알림 ID
     * @return 삭제 성공 여부
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        log.info("DELETE /api/alerts/{} - 알림 삭제 요청", id);

        if (!alertRepository.existsById(id)) {
            log.error("알림을 찾을 수 없습니다: ID = {}", id);
            return ResponseEntity.notFound().build();
        }

        alertRepository.deleteById(id);
        log.info("알림 삭제 완료: ID = {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 모든 읽은 알림 삭제
     * DELETE /api/alerts/sent
     * @return 삭제된 알림 개수
     */
    @DeleteMapping("/sent")
    public ResponseEntity<String> deleteSentAlerts() {
        log.info("DELETE /api/alerts/sent - 읽은 알림 전체 삭제 요청");

        try {
            List<Alert> sentAlerts = alertRepository.findBySent(true);
            int deletedCount = sentAlerts.size();
            alertRepository.deleteAll(sentAlerts);

            log.info("읽은 알림 전체 삭제 완료: {} 건", deletedCount);
            return ResponseEntity.ok(deletedCount + "건의 알림이 삭제되었습니다.");
        } catch (Exception e) {
            log.error("알림 삭제 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("알림 삭제 중 오류가 발생했습니다.");
        }
    }
}
