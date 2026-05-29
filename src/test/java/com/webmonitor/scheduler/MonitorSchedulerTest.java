package com.webmonitor.scheduler;

import com.webmonitor.service.MonitorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * MonitorScheduler 스케줄러 테스트
 * 비즈니스 로직 검증 (실제 스케줄링은 테스트하지 않음)
 */
@ExtendWith(MockitoExtension.class)
class MonitorSchedulerTest {

    @Mock
    private MonitorService monitorService;

    @InjectMocks
    private MonitorScheduler monitorScheduler;

    @Test
    @DisplayName("monitorSitesEveryMinute 정상 실행 - MonitorService 호출 확인")
    void monitorSitesEveryMinute_정상실행_MonitorService호출확인() {
        // When: 스케줄러 메서드 호출
        monitorScheduler.monitorSitesEveryMinute();

        // Then: MonitorService.monitorAllActiveSites()가 1번 호출되어야 함
        verify(monitorService, times(1)).monitorAllActiveSites();
    }

    @Test
    @DisplayName("monitorSitesEveryMinute 예외 발생 시 예외 로그 기록 후 계속 진행")
    void monitorSitesEveryMinute_예외발생_예외로그기록후계속진행() {
        // Given: MonitorService에서 예외 발생
        doThrow(new RuntimeException("모니터링 중 오류 발생"))
                .when(monitorService).monitorAllActiveSites();

        // When: 스케줄러 메서드 호출 (예외가 스케줄러를 중단시키지 않아야 함)
        monitorScheduler.monitorSitesEveryMinute();

        // Then: 예외가 발생해도 메서드는 정상적으로 종료되어야 함
        verify(monitorService, times(1)).monitorAllActiveSites();
    }
}
