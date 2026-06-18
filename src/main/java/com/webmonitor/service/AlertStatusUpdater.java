package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.exception.resource.AlertNotFoundException;
import com.webmonitor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertStatusUpdater {

    private final AlertRepository alertRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAlertStatus(Long alertId, boolean sendSuccess, String errorMessage) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));

        if (sendSuccess) {
            alert.setSent(true);
            alert.setSentAt(LocalDateTime.now());
            alert.setLastErrorMessage(null);
            alertRepository.save(alert);
            log.info("알림 발송 완료 - ID: {}", alert.getId());
        } else {
            alert.incrementRetryCount();
            alert.setLastErrorMessage(errorMessage);
            alertRepository.save(alert);
            log.warn("재시도 카운트 증가 - ID: {}, retryCount: {}/{}",
                    alertId, alert.getRetryCount(), Alert.MAX_RETRIES);
            if (!alert.canRetry()) {
                log.error("최대 재시도 횟수 도달 - 수동 확인 필요: ID = {}", alertId);
            }
        }
    }
}
