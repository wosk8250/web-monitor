package com.webmonitor.service;

import com.webmonitor.domain.Alert;
import com.webmonitor.domain.Site;
import com.webmonitor.dto.AlertResponse;
import com.webmonitor.dto.SettingResponse;
import com.webmonitor.exception.resource.AlertNotFoundException;
import com.webmonitor.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

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
    private final SettingService settingService;
    private final AlertStatusUpdater alertStatusUpdater;

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
                .orElseThrow(() -> new AlertNotFoundException(alertId));

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
            SettingResponse setting = settingService.findActiveSetting().orElse(null);
            if (setting == null || !setting.enabled()
                    || setting.discordWebhookUrl() == null
                    || setting.discordWebhookUrl().trim().isEmpty()) {
                log.debug("활성화된 Discord 설정이 없어 알림 전송 건너뜀 - ID: {}", alert.getId());
                sendSuccess = true; // 설정 없음 = 전송 불필요, 재시도 불필요
            } else {
                discordService.sendAlert(setting.discordWebhookUrl(), alert);
                sendSuccess = true;
                log.info("Discord 알림 발송 성공 - ID: {}", alert.getId());
            }
        } catch (Exception e) {
            log.error("Discord 알림 발송 실패 - ID: {}, 에러: {}", alert.getId(), e.getMessage());
            errorMessage = e.getMessage();
        }

        // 4. 결과를 별도 트랜잭션으로 업데이트
        alertStatusUpdater.updateAlertStatus(alertId, sendSuccess, errorMessage);
    }

    public List<Alert> getAllAlerts() {
        return alertRepository.findAllByOrderByDetectedAtDesc();
    }

    public List<Alert> getUnsentAlerts() {
        return alertRepository.findBySentOrderByPriorityDescDetectedAtDesc(false);
    }

    public List<Alert> getSentAlerts() {
        return alertRepository.findBySentOrderByDetectedAtDesc(true);
    }

    public List<Alert> getAlertsBySite(Long siteId) {
        return alertRepository.findBySiteIdOrderByDetectedAtDesc(siteId);
    }

    public List<Alert> getAlertsByKeyword(Long keywordId) {
        return alertRepository.findByKeywordIdOrderByDetectedAtDesc(keywordId);
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
     * ID로 알림 조회 (없으면 AlertNotFoundException)
     */
    public Alert getAlertById(Long id) {
        log.debug("알림 조회 - ID: {}", id);
        return alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
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
                .orElseThrow(() -> new AlertNotFoundException(id));

        alert.setSent(true);
        alert.setSentAt(LocalDateTime.now());
        alert.setLastErrorMessage(null);
        Alert saved = alertRepository.save(alert);

        log.info("알림 발송 완료 표시 - ID: {}", id);
        return saved;
    }

    /**
     * 알림 일괄 발송 완료 표시
     */
    @Transactional
    public int markAsSentBulk(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        int updated = alertRepository.markAsSentByIds(ids, LocalDateTime.now());
        log.info("알림 일괄 발송 완료 표시 - {} 건", updated);
        return updated;
    }

    /**
     * 알림 삭제
     */
    @Transactional
    public void deleteAlert(Long id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));
        alertRepository.delete(alert);
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

    @Transactional
    public void cleanupAllExcessAlerts(int maxAlertsPerSite) {
        List<Site> sites = alertRepository.findSitesWithAlertCountExceeding(maxAlertsPerSite);
        for (Site site : sites) {
            cleanupExcessAlerts(site, maxAlertsPerSite);
        }
        if (!sites.isEmpty()) {
            log.info("초과 알림 정리 완료: {} 개 사이트 처리", sites.size());
        }
    }

    // ==================== SiteService 캐스케이드 삭제용 ====================

    @Transactional
    public void deleteAlertsBySiteId(Long siteId) {
        alertRepository.deleteBySiteId(siteId);
        log.debug("사이트 ID {}의 모든 알림 삭제 완료", siteId);
    }

    // ==================== MonitorService 알림 정리용 ====================

    @Transactional
    public void cleanupExcessAlerts(Site site, int maxAlerts) {
        long alertCount = alertRepository.countBySite(site);
        if (alertCount > maxAlerts) {
            int deleteCount = (int) (alertCount - maxAlerts);
            Pageable pageable = PageRequest.of(0, deleteCount);
            Page<Alert> oldAlerts = alertRepository.findBySiteOrderByDetectedAtAsc(site, pageable);
            if (!oldAlerts.isEmpty()) {
                alertRepository.deleteAll(oldAlerts.getContent());
                log.info("사이트 '{}' 알림 정리 완료: {} 개 삭제 (현재: {} -> {})",
                        site.getName(), oldAlerts.getContent().size(), alertCount, maxAlerts);
            }
        }
    }

    // ==================== Controller 전용 DTO 반환 메서드 ====================

    public List<AlertResponse> getAllAlertResponses() {
        return getAllAlerts().stream().map(AlertResponse::from).toList();
    }

    public AlertResponse getAlertResponseById(Long id) {
        return AlertResponse.from(getAlertById(id));
    }

    public List<AlertResponse> getUnsentAlertResponses() {
        return getUnsentAlerts().stream().map(AlertResponse::from).toList();
    }

    public List<AlertResponse> getSentAlertResponses() {
        return getSentAlerts().stream().map(AlertResponse::from).toList();
    }

    public List<AlertResponse> getAlertResponsesBySite(Long siteId) {
        return getAlertsBySite(siteId).stream().map(AlertResponse::from).toList();
    }

    public List<AlertResponse> getAlertResponsesByKeyword(Long keywordId) {
        return getAlertsByKeyword(keywordId).stream().map(AlertResponse::from).toList();
    }

    public List<AlertResponse> getAlertResponsesByPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        return getAlertsByPeriod(startDate, endDate).stream().map(AlertResponse::from).toList();
    }

    public AlertResponse markAsSentResponse(Long id) {
        return AlertResponse.from(markAsSent(id));
    }
}
