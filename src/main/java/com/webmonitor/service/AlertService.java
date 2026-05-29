package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 알림 서비스
 * 우선순위 기반 알림 처리:
 * HIGH 우선순위 (재고 알림): 즉시 발송
 * NORMAL 우선순위 (새글 알림): 대기열 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AlertService {

    private final AlertRepository alertRepository;
    private final DiscordService discordService;

    @Value("${discord.webhook.url:}")
    private String webhookUrl;

    /**
     * 알림 생성 및 우선순위 처리
     * HIGH 우선순위: 트랜잭션 커밋 후 즉시 발송 시도 (비동기)
     * NORMAL 우선순위: DB 큐에 저장 (스케줄러가 처리)
     */
    @Transactional
    public Alert createAlert(Alert alert) {
        // Alert 저장 (DB에 커밋)
        Alert savedAlert = alertRepository.save(alert);
        log.info("알림 생성 완료 - ID: {}, Priority: {}, Type: {}",
                savedAlert.getId(), savedAlert.getPriority(), savedAlert.getAlertType());

        // HIGH 우선순위면 트랜잭션 커밋 후 즉시 발송 시도
        if (savedAlert.getPriority() == Alert.Priority.HIGH) {
            log.info("HIGH 우선순위 알림 감지 - 트랜잭션 커밋 후 발송 등록: ID = {}", savedAlert.getId());

            // 트랜잭션 커밋 후 실행되도록 등록
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("트랜잭션 커밋 완료 - 비동기 발송 시작: ID = {}", savedAlert.getId());
                    sendAlertAsync(savedAlert.getId());
                }
            });
        } else {
            log.info("NORMAL 우선순위 알림 - 대기열에 추가됨: ID = {}", savedAlert.getId());
        }

        return savedAlert;
    }

    /**
     * 알림 비동기 발송 (ID 기반)
     * LazyInitializationException 방지를 위해 ID를 받아서 재조회
     * @Transactional과 분리하여 트랜잭션 커밋 후 실행 보장
     */
    @Async("alertExecutor")
    public void sendAlertAsync(Long alertId) {
        log.info("비동기 알림 발송 시작 - ID: {}", alertId);

        // ID로 Alert 재조회 후 발송 시도
        sendAlertWithRetry(alertId);
    }

    /**
     * 재시도 로직이 포함된 알림 발송
     * 외부 API 호출은 트랜잭션 밖에서 처리하여 DB 커넥션 점유 최소화
     */
    public void sendAlertWithRetry(Long alertId) {
        // 1. 조회만 수행 (트랜잭션 없음)
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. ID: " + alertId));

        // 2. 재시도 가능 여부 확인
        if (!alert.canRetry()) {
            log.warn("최대 재시도 횟수 초과 - ID: {}, retryCount: {}", alertId, alert.getRetryCount());
            return;
        }

        log.info("알림 발송 시도 - ID: {}, Priority: {}, retryCount: {}",
                alert.getId(), alert.getPriority(), alert.getRetryCount());

        // 3. 외부 API 호출 (트랜잭션 밖)
        boolean sendSuccess = false;
        String errorMessage = null;

        try {
            discordService.sendAlert(webhookUrl, alert);
            sendSuccess = true;
            log.info("Discord 알림 발송 성공 - ID: {}", alert.getId());
        } catch (Exception e) {
            log.error("Discord 알림 발송 실패 - ID: {}, 에러: {}", alert.getId(), e.getMessage());
            errorMessage = e.getMessage();
        }

        // 4. 결과를 별도 트랜잭션으로 업데이트
        updateAlertStatus(alertId, sendSuccess, errorMessage);
    }

    /**
     * 알림 발송 결과 업데이트 (별도 트랜잭션)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void updateAlertStatus(Long alertId, boolean sendSuccess, String errorMessage) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. ID: " + alertId));

        if (sendSuccess) {
            // 전송 성공
            alert.setSent(true);
            alert.setSentAt(LocalDateTime.now());
            alert.setLastErrorMessage(null);
            alertRepository.save(alert);
            log.info("알림 발송 완료 - ID: {}", alert.getId());

        } else {
            // 전송 실패
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

    /**
     * 전체 알림 조회 (페이지네이션)
     */
    public Page<Alert> getAllAlerts(Pageable pageable) {
        log.debug("전체 알림 조회 (페이지네이션) - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());
        return alertRepository.findAllByOrderByDetectedAtDesc(pageable);
    }

    /**
     * 미발송 알림 조회 (페이지네이션)
     */
    public Page<Alert> getUnsentAlerts(Pageable pageable) {
        log.debug("미발송 알림 조회 (페이지네이션)");
        return alertRepository.findBySentOrderByPriorityDescDetectedAtDesc(false, pageable);
    }

    /**
     * 발송 완료 알림 조회 (페이지네이션)
     */
    public Page<Alert> getSentAlerts(Pageable pageable) {
        log.debug("발송 완료 알림 조회 (페이지네이션)");
        return alertRepository.findBySentOrderByDetectedAtDesc(true, pageable);
    }

    /**
     * 특정 사이트 알림 조회 (페이지네이션)
     */
    public Page<Alert> getAlertsBySite(Long siteId, Pageable pageable) {
        log.debug("사이트별 알림 조회 (페이지네이션) - siteId: {}", siteId);
        return alertRepository.findBySiteIdOrderByDetectedAtDesc(siteId, pageable);
    }

    /**
     * 특정 키워드 알림 조회 (페이지네이션)
     */
    public Page<Alert> getAlertsByKeyword(Long keywordId, Pageable pageable) {
        log.debug("키워드별 알림 조회 (페이지네이션) - keywordId: {}", keywordId);
        return alertRepository.findByKeywordIdOrderByDetectedAtDesc(keywordId, pageable);
    }

    /**
     * ID로 알림 조회
     */
    public Optional<Alert> getAlertById(Long id) {
        log.debug("알림 조회 - ID: {}", id);
        return alertRepository.findById(id);
    }

    /**
     * 특정 기간 알림 조회
     */
    public List<Alert> getAlertsByPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("기간별 알림 조회 - {} ~ {}", startDate, endDate);
        return alertRepository.findByDetectedAtBetween(startDate, endDate);
    }

    /**
     * 알림을 발송 완료로 표시
     */
    @Transactional
    public Alert markAsSent(Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. ID: " + id));

        alert.setSent(true);
        alert.setSentAt(LocalDateTime.now());
        Alert saved = alertRepository.save(alert);

        log.info("알림 발송 완료 표시 - ID: {}", id);
        return saved;
    }

    /**
     * 알림 삭제
     */
    @Transactional
    public void deleteAlert(Long id) {
        if (!alertRepository.existsById(id)) {
            throw new IllegalArgumentException("알림을 찾을 수 없습니다. ID: " + id);
        }
        alertRepository.deleteById(id);
        log.info("알림 삭제 완료 - ID: {}", id);
    }

    /**
     * 전송된 알림 일괄 삭제 (표준 JPA Pageable 방식)
     */
    @Transactional
    public int deleteSentAlerts() {
        int totalDeleted = 0;
        int batchSize = 1000;

        // Pageable 기반으로 배치 삭제
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, batchSize);
        org.springframework.data.domain.Page<com.webmonitor.domain.Alert> page;

        do {
            page = alertRepository.findBySentOrderByDetectedAtDesc(true, pageable);
            if (!page.isEmpty()) {
                alertRepository.deleteAll(page.getContent());
                totalDeleted += page.getNumberOfElements();
            }
        } while (!page.isEmpty());

        if (totalDeleted > 0) {
            log.info("전송된 알림 일괄 삭제 완료 - {} 건", totalDeleted);
        }
        return totalDeleted;
    }

    /**
     * 전체 알림 삭제
     */
    @Transactional
    public int deleteAllAlerts() {
        long count = alertRepository.count();
        if (count > 0) {
            alertRepository.deleteAll();
            log.info("전체 알림 삭제 완료 - {} 건", count);
        }
        return (int) count;
    }
}
