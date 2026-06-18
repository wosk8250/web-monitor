package com.webmonitor.scheduler;

import com.webmonitor.domain.Alert;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 오래된 알림을 자동으로 정리하는 스케줄러
 * 알림 타입별로 다른 보관 기간 적용:
 * - 키워드/내용 변경 알림: 7일
 * - 재고 알림: 30일
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "alert.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class AlertCleanupScheduler {

    private final AlertRepository alertRepository;
    private final AlertService alertService;

    @Value("${alert.cleanup.keyword-days:7}")
    private int keywordRetentionDays;

    @Value("${alert.cleanup.restock-days:30}")
    private int restockRetentionDays;

    @Value("${monitor.alert.max-per-site:50}")
    private int maxAlertsPerSite;

    /**
     * 오래된 알림 자동 정리
     * 기본값: 매일 새벽 3시 실행
     * 키워드/내용 변경 알림: 7일 이전 삭제
     * 재고 알림: 30일 이전 삭제
     */
    @Scheduled(cron = "${alert.cleanup.cron:0 0 3 * * *}")
    public void cleanupOldAlerts() {
        try {
            log.info("========================================");
            log.info("오래된 알림 정리 스케줄러 시작");
            log.info("========================================");

            int totalDeleted = 0;

            // 1. 키워드 알림 정리 (keywordRetentionDays일, 기본 7일)
            LocalDateTime shortTermCutoff = LocalDateTime.now().minusDays(keywordRetentionDays);
            int keywordDeleted = alertRepository.deleteByAlertTypeAndDetectedAtBefore(Alert.AlertType.KEYWORD, shortTermCutoff);
            if (keywordDeleted > 0) {
                log.info("키워드 알림 {}일 이전: {}건 삭제", keywordRetentionDays, keywordDeleted);
                totalDeleted += keywordDeleted;
            }

            // 2. 내용 변경 알림 정리 (동일 보존 기간 적용)
            int contentChangeDeleted = alertRepository.deleteByAlertTypeAndDetectedAtBefore(Alert.AlertType.CONTENT_CHANGE, shortTermCutoff);
            if (contentChangeDeleted > 0) {
                log.info("내용 변경 알림 {}일 이전: {}건 삭제", keywordRetentionDays, contentChangeDeleted);
                totalDeleted += contentChangeDeleted;
            }

            // 3. 재고 알림 정리 (30일)
            LocalDateTime restockCutoff = LocalDateTime.now().minusDays(restockRetentionDays);
            int restockDeleted = alertRepository.deleteByAlertTypeAndDetectedAtBefore(Alert.AlertType.PRODUCT_RESTOCK, restockCutoff);
            if (restockDeleted > 0) {
                log.info("재고 알림 {}일 이전: {}건 삭제", restockRetentionDays, restockDeleted);
                totalDeleted += restockDeleted;
            }

            if (totalDeleted > 0) {
                log.info("총 {}건 알림 삭제 완료", totalDeleted);
            } else {
                log.info("삭제할 오래된 알림이 없습니다.");
            }

            log.info("========================================");

        } catch (Exception e) {
            log.error("알림 정리 스케줄러 실행 중 오류 발생", e);
        }
    }

    /**
     * 사이트별 최대 알림 수 초과분 정리 (5분마다)
     * 모니터링 루프에서 게시글마다 실행하던 것을 주기적 배치로 대체
     */
    @Scheduled(fixedDelayString = "${alert.cleanup.excess-delay:300000}")
    public void cleanupExcessAlerts() {
        try {
            alertService.cleanupAllExcessAlerts(maxAlertsPerSite);
        } catch (Exception e) {
            log.error("초과 알림 정리 중 오류 발생", e);
        }
    }
}
