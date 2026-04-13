package com.webmonitor.scheduler;

import com.webmonitor.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 웹사이트 모니터링을 주기적으로 실행하는 스케줄러
 */
@Component // Spring 컴포넌트로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성 (의존성 주입)
@Slf4j // 로그 사용을 위한 Logger 자동 생성
public class MonitorScheduler {

    private final MonitorService monitorService;

    // 날짜 시간 포맷터
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 1분마다 활성화된 사이트들을 자동으로 체크
     * cron 표현식: "초 분 시 일 월 요일"
     * fixedDelay: 이전 작업 완료 후 지정된 시간(밀리초) 후 실행
     * fixedRate: 지정된 주기(밀리초)마다 실행 (이전 작업 완료 여부와 무관)
     */
    @Scheduled(fixedRate = 60000) // 60000ms = 60초 = 1분마다 실행
    public void monitorSitesEveryMinute() {
        String currentTime = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        log.info("========================================");
        log.info("스케줄러 실행 시작: {}", currentTime);
        log.info("========================================");

        try {
            // MonitorService를 호출하여 모든 활성화된 사이트 모니터링
            monitorService.monitorAllActiveSites();

            log.info("스케줄러 실행 완료: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));

        } catch (Exception e) {
            log.error("스케줄러 실행 중 오류 발생: {}", e.getMessage(), e);
        }

        log.info("========================================");
    }

    /**
     * 5분마다 실행되는 모니터링 (선택 사항 - 주석 처리됨)
     * 필요시 주석을 해제하여 사용
     */
    // @Scheduled(fixedRate = 300000) // 300000ms = 5분
    // public void monitorSitesEveryFiveMinutes() {
    //     log.info("5분 주기 모니터링 시작: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));
    //     monitorService.monitorAllActiveSites();
    // }

    /**
     * 매일 자정에 실행되는 모니터링 (선택 사항 - 주석 처리됨)
     * cron 표현식 사용 예시
     */
    // @Scheduled(cron = "0 0 0 * * *") // 매일 00:00:00에 실행
    // public void monitorSitesDaily() {
    //     log.info("일일 모니터링 시작: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));
    //     monitorService.monitorAllActiveSites();
    // }

    /**
     * 평일 오전 9시에 실행되는 모니터링 (선택 사항 - 주석 처리됨)
     * cron 표현식: "초 분 시 일 월 요일"
     * 요일: 1(월) ~ 5(금)
     */
    // @Scheduled(cron = "0 0 9 * * 1-5") // 평일(월~금) 오전 9시에 실행
    // public void monitorSitesWeekdayMorning() {
    //     log.info("평일 오전 모니터링 시작: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));
    //     monitorService.monitorAllActiveSites();
    // }

    /**
     * 애플리케이션 시작 후 10초 뒤 최초 1회 실행, 이후 1분마다 실행 (선택 사항 - 주석 처리됨)
     */
    // @Scheduled(initialDelay = 10000, fixedRate = 60000)
    // public void monitorSitesWithInitialDelay() {
    //     log.info("초기 딜레이 후 모니터링: {}", LocalDateTime.now().format(DATE_TIME_FORMATTER));
    //     monitorService.monitorAllActiveSites();
    // }
}
