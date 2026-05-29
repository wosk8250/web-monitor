package com.webmonitor.scheduler;

import com.webmonitor.service.ProductMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 제품 재고 모니터링 스케줄러
 * 우선순위별로 다른 주기로 모니터링 실행
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductMonitorScheduler {

    private final ProductMonitorService productMonitorService;

    /**
     * 긴급 우선순위 제품 모니터링 (URGENT)
     * 30초마다 실행
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    public void monitorUrgentProductsEvery30Seconds() {
        try {
            log.debug("긴급 제품 모니터링 스케줄러 시작 (30초 주기)");
            productMonitorService.monitorUrgentProducts();
        } catch (Exception e) {
            log.error("긴급 제품 모니터링 스케줄러 실행 중 오류", e);
        }
    }

    /**
     * 일반 우선순위 제품 모니터링 (NORMAL)
     * 60초(1분)마다 실행
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 10000)
    public void monitorNormalProductsEveryMinute() {
        try {
            log.debug("일반 제품 모니터링 스케줄러 시작 (60초 주기)");
            productMonitorService.monitorNormalProducts();
        } catch (Exception e) {
            log.error("일반 제품 모니터링 스케줄러 실행 중 오류", e);
        }
    }
}
