package com.webmonitor.scheduler;

import com.webmonitor.domain.Alert;
import com.webmonitor.repository.AlertRepository;
import com.webmonitor.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NORMAL 우선순위 알림 (새글 알림) 대기열 처리 스케줄러
 * 2초마다 미발송 알림을 10개씩 배치로 처리 발송
 * HIGH 우선순위 알림은 즉시 발송되므로 여기서는 NORMAL만 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "alert.queue.enabled", havingValue = "true", matchIfMissing = true)
public class AlertQueueProcessor {

    private final AlertService alertService;
    private final AlertRepository alertRepository;

    @Value("${alert.queue.batch-size:10}")
    private int batchSize;

    /**
     * 대기열의 미발송 알림을 처리하는 스케줄러
     * fixedDelay = 2000ms (2초마다 실행)
     * NORMAL 우선순위 알림만 처리 (HIGH는 즉시 발송되므로 제외)
     */
    @Scheduled(fixedDelayString = "${alert.queue.fixed-delay:2000}")
    public void processAlertQueue() {
        try {
            // NORMAL 우선순위 + 재시도 가능한 알림만 조회 (최적화)
            Pageable pageable = PageRequest.of(0, batchSize);
            Page<Alert> pendingAlerts = alertRepository.findBySentAndPriorityAndRetryCountLessThanOrderByDetectedAtDesc(
                    false,
                    Alert.Priority.NORMAL,
                    Alert.MAX_RETRIES,
                    pageable
            );

            int processedCount = 0;

            for (Alert alert : pendingAlerts.getContent()) {
                try {
                    // 알림 발송 (재시도 로직 포함)
                    alertService.sendAlertWithRetry(alert.getId());
                    processedCount++;

                } catch (Exception e) {
                    log.error("대기열 알림 발송 실패 - ID: {}, 에러: {}", alert.getId(), e.getMessage());
                    // 실패해도 계속 진행 (재시도 로직은 sendAlertWithRetry에서 처리)
                }
            }

            if (processedCount > 0) {
                log.info("대기열 처리 완료 - 처리: {} / 전체 미발송: {}",
                        processedCount, pendingAlerts.getTotalElements());
            }

        } catch (Exception e) {
            log.error("대기열 처리 중 에러 발생", e);
        }
    }
}
