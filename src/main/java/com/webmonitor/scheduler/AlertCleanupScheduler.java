package com.webmonitor.scheduler;

import com.webmonitor.domain.Alert;
import com.webmonitor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

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

    @Value("${alert.cleanup.keyword-days:7}")
    private int keywordRetentionDays;

    @Value("${alert.cleanup.restock-days:30}")
    private int restockRetentionDays;

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
            int batchSize = 1000; // 한 번에 삭제할 배치 크기

            // 1. 키워드 알림 정리 (7일)
            LocalDateTime keywordCutoff = LocalDateTime.now().minusDays(keywordRetentionDays);
            int keywordDeleted = deleteOldAlertsByTypeBatch(Alert.AlertType.KEYWORD, keywordCutoff, batchSize);
            if (keywordDeleted > 0) {
                log.info("키워드 알림 {}일 이전: {}건 삭제", keywordRetentionDays, keywordDeleted);
                totalDeleted += keywordDeleted;
            }

            // 2. 내용 변경 알림 정리 (7일)
            int contentChangeDeleted = deleteOldAlertsByTypeBatch(Alert.AlertType.CONTENT_CHANGE, keywordCutoff, batchSize);
            if (contentChangeDeleted > 0) {
                log.info("내용 변경 알림 {}일 이전: {}건 삭제", keywordRetentionDays, contentChangeDeleted);
                totalDeleted += contentChangeDeleted;
            }

            // 3. 재고 알림 정리 (30일)
            LocalDateTime restockCutoff = LocalDateTime.now().minusDays(restockRetentionDays);
            int restockDeleted = deleteOldAlertsByTypeBatch(Alert.AlertType.PRODUCT_RESTOCK, restockCutoff, batchSize);
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
     * 특정 타입의 오래된 알림을 배치 방식으로 삭제 (표준 JPA 방식)
     * 메모리 효율적으로 대량 삭제 처리
     */
    private int deleteOldAlertsByTypeBatch(Alert.AlertType alertType, LocalDateTime cutoffDate, int batchSize) {
        int totalDeleted = 0;

        // 오래된 알림 조회 후 타입 필터링하여 배치 삭제
        List<Alert> oldAlerts;
        do {
            oldAlerts = alertRepository.findByDetectedAtBefore(cutoffDate).stream()
                    .filter(alert -> alert.getAlertType() == alertType)
                    .limit(batchSize)
                    .toList();

            if (!oldAlerts.isEmpty()) {
                alertRepository.deleteAll(oldAlerts);
                totalDeleted += oldAlerts.size();
            }
        } while (oldAlerts.size() == batchSize);

        return totalDeleted;
    }
}
