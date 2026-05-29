package com.webmonitor.scheduler;

import com.webmonitor.service.ProductMonitorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * ProductMonitorScheduler 스케줄러 테스트
 * 우선순위별 제품 모니터링 스케줄러 테스트
 */
@ExtendWith(MockitoExtension.class)
class ProductMonitorSchedulerTest {

    @Mock
    private ProductMonitorService productMonitorService;

    @InjectMocks
    private ProductMonitorScheduler productMonitorScheduler;

    @Test
    @DisplayName("긴급 제품 모니터링(30초) 정상 실행 - URGENT 우선순위 제품 모니터링")
    void monitorUrgentProducts_정상실행_URGENT우선순위제품모니터링() {
        // When: 긴급 제품 모니터링 스케줄러 실행
        productMonitorScheduler.monitorUrgentProductsEvery30Seconds();

        // Then: ProductMonitorService.monitorUrgentProducts() 호출 확인
        verify(productMonitorService, times(1)).monitorUrgentProducts();
    }

    @Test
    @DisplayName("긴급 제품 모니터링 예외 발생 시 로그 기록 후 계속 진행")
    void monitorUrgentProducts_예외발생_로그기록후계속진행() {
        // Given: ProductMonitorService에서 예외 발생
        doThrow(new RuntimeException("긴급 제품 모니터링 중 오류"))
                .when(productMonitorService).monitorUrgentProducts();

        // When: 스케줄러 메서드 호출
        productMonitorScheduler.monitorUrgentProductsEvery30Seconds();

        // Then: 예외가 발생해도 스케줄러는 계속 동작해야 함
        verify(productMonitorService, times(1)).monitorUrgentProducts();
    }

    @Test
    @DisplayName("일반 제품 모니터링(60초) 정상 실행 - NORMAL 우선순위 제품 모니터링")
    void monitorNormalProducts_정상실행_NORMAL우선순위제품모니터링() {
        // When: 일반 제품 모니터링 스케줄러 실행
        productMonitorScheduler.monitorNormalProductsEveryMinute();

        // Then: ProductMonitorService.monitorNormalProducts() 호출 확인
        verify(productMonitorService, times(1)).monitorNormalProducts();
    }

    @Test
    @DisplayName("일반 제품 모니터링 예외 발생 시 로그 기록 후 계속 진행")
    void monitorNormalProducts_예외발생_로그기록후계속진행() {
        // Given: ProductMonitorService에서 예외 발생
        doThrow(new RuntimeException("일반 제품 모니터링 중 오류"))
                .when(productMonitorService).monitorNormalProducts();

        // When: 스케줄러 메서드 호출
        productMonitorScheduler.monitorNormalProductsEveryMinute();

        // Then: 예외가 발생해도 스케줄러는 계속 동작해야 함
        verify(productMonitorService, times(1)).monitorNormalProducts();
    }
}
