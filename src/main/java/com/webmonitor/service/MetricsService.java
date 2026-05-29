package com.webmonitor.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

/**
 * 메트릭 수집 서비스
 * Micrometer를 사용하여 시스템 메트릭을 수집하고 관리합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // ========================================
    // COUNTER METRICS
    // ========================================

    /**
     * 예외 발생 카운터 증가
     *
     * @param exceptionType 예외 타입 (예: "SocketTimeoutException")
     * @param location      발생 위치 (예: "MonitorService", "AsyncConfig.alertExecutor")
     */
    public void incrementExceptionCounter(String exceptionType, String location) {
        Counter.builder("webmonitor.exception.count")
                .tag("exception.type", exceptionType)
                .tag("location", location)
                .description("발생한 예외 횟수")
                .register(meterRegistry)
                .increment();

        log.debug("예외 카운터 증가: type={}, location={}", exceptionType, location);
    }

    /**
     * 크롤링 성공 카운터 증가
     *
     * @param siteUrl 사이트 URL
     */
    public void incrementCrawlSuccessCounter(String siteUrl) {
        Counter.builder("webmonitor.crawl.success")
                .tag("site.url", siteUrl)
                .description("크롤링 성공 횟수")
                .register(meterRegistry)
                .increment();

        log.debug("크롤링 성공 카운터 증가: site={}", siteUrl);
    }

    /**
     * 크롤링 실패 카운터 증가
     *
     * @param siteUrl 사이트 URL
     * @param reason  실패 이유 (예: "timeout", "connection_error", "unknown_host")
     */
    public void incrementCrawlFailureCounter(String siteUrl, String reason) {
        Counter.builder("webmonitor.crawl.failure")
                .tag("site.url", siteUrl)
                .tag("reason", reason)
                .description("크롤링 실패 횟수")
                .register(meterRegistry)
                .increment();

        log.debug("크롤링 실패 카운터 증가: site={}, reason={}", siteUrl, reason);
    }

    /**
     * 알림 발송 성공 카운터 증가
     *
     * @param priority 알림 우선순위 (예: "HIGH", "NORMAL")
     */
    public void incrementAlertSentCounter(String priority) {
        Counter.builder("webmonitor.alert.sent")
                .tag("priority", priority)
                .description("알림 발송 성공 횟수")
                .register(meterRegistry)
                .increment();

        log.debug("알림 발송 성공 카운터 증가: priority={}", priority);
    }

    /**
     * 알림 발송 실패 카운터 증가
     *
     * @param priority 알림 우선순위
     * @param reason   실패 이유
     */
    public void incrementAlertFailureCounter(String priority, String reason) {
        Counter.builder("webmonitor.alert.failure")
                .tag("priority", priority)
                .tag("reason", reason)
                .description("알림 발송 실패 횟수")
                .register(meterRegistry)
                .increment();

        log.debug("알림 발송 실패 카운터 증가: priority={}, reason={}", priority, reason);
    }

    /**
     * Thread Pool 작업 거부 카운터 증가
     *
     * @param poolName Thread Pool 이름 (예: "alert", "productMonitor")
     */
    public void incrementThreadPoolRejectionCounter(String poolName) {
        Counter.builder("webmonitor.threadpool.rejection")
                .tag("pool.name", poolName)
                .description("Thread Pool 작업 거부 횟수")
                .register(meterRegistry)
                .increment();

        log.warn("Thread Pool 작업 거부 카운터 증가: pool={}", poolName);
    }

    /**
     * 키워드 매칭 카운터 증가
     *
     * @param keyword 매칭된 키워드
     * @param siteUrl 사이트 URL
     */
    public void incrementKeywordMatchCounter(String keyword, String siteUrl) {
        Counter.builder("webmonitor.keyword.match")
                .tag("keyword", keyword)
                .tag("site.url", siteUrl)
                .description("키워드 매칭 횟수")
                .register(meterRegistry)
                .increment();

        log.debug("키워드 매칭 카운터 증가: keyword={}, site={}", keyword, siteUrl);
    }

    // ========================================
    // GAUGE METRICS
    // ========================================

    /**
     * Thread Pool 활성 스레드 수 게이지 등록
     *
     * @param poolName Thread Pool 이름
     * @param executor ThreadPoolExecutor 인스턴스
     */
    public void registerThreadPoolActiveThreadsGauge(String poolName, ThreadPoolExecutor executor) {
        Gauge.builder("webmonitor.threadpool.active.threads", executor, ThreadPoolExecutor::getActiveCount)
                .tag("pool.name", poolName)
                .description("Thread Pool 활성 스레드 수")
                .register(meterRegistry);

        log.debug("Thread Pool 활성 스레드 게이지 등록: pool={}", poolName);
    }

    /**
     * Thread Pool 큐 사이즈 게이지 등록
     *
     * @param poolName Thread Pool 이름
     * @param executor ThreadPoolExecutor 인스턴스
     */
    public void registerThreadPoolQueueSizeGauge(String poolName, ThreadPoolExecutor executor) {
        Gauge.builder("webmonitor.threadpool.queue.size", executor, e -> e.getQueue().size())
                .tag("pool.name", poolName)
                .description("Thread Pool 큐 사이즈")
                .register(meterRegistry);

        log.debug("Thread Pool 큐 사이즈 게이지 등록: pool={}", poolName);
    }

    /**
     * Thread Pool 완료된 작업 수 게이지 등록
     *
     * @param poolName Thread Pool 이름
     * @param executor ThreadPoolExecutor 인스턴스
     */
    public void registerThreadPoolCompletedTasksGauge(String poolName, ThreadPoolExecutor executor) {
        Gauge.builder("webmonitor.threadpool.completed.tasks", executor, ThreadPoolExecutor::getCompletedTaskCount)
                .tag("pool.name", poolName)
                .description("Thread Pool 완료된 작업 수")
                .register(meterRegistry);

        log.debug("Thread Pool 완료된 작업 수 게이지 등록: pool={}", poolName);
    }

    /**
     * 커스텀 게이지 등록
     *
     * @param name           메트릭 이름
     * @param description    메트릭 설명
     * @param valueSupplier  값을 제공하는 Supplier
     * @param tags           태그 (key-value 쌍으로 전달, 짝수 개여야 함)
     */
    public void registerCustomGauge(String name, String description, Supplier<Number> valueSupplier, String... tags) {
        Gauge.builder(name, valueSupplier, s -> s.get().doubleValue())
                .tags(tags)
                .description(description)
                .register(meterRegistry);

        log.debug("커스텀 게이지 등록: name={}", name);
    }

    // ========================================
    // TIMER METRICS
    // ========================================

    /**
     * 크롤링 응답 시간 기록
     *
     * @param siteUrl  사이트 URL
     * @param duration 소요 시간
     */
    public void recordCrawlDuration(String siteUrl, Duration duration) {
        Timer.builder("webmonitor.crawl.duration")
                .tag("site.url", siteUrl)
                .description("크롤링 응답 시간")
                .register(meterRegistry)
                .record(duration);

        log.debug("크롤링 응답 시간 기록: site={}, duration={}ms", siteUrl, duration.toMillis());
    }

    /**
     * 알림 발송 시간 기록
     *
     * @param priority 알림 우선순위
     * @param duration 소요 시간
     */
    public void recordAlertSendDuration(String priority, Duration duration) {
        Timer.builder("webmonitor.alert.send.duration")
                .tag("priority", priority)
                .description("알림 발송 소요 시간")
                .register(meterRegistry)
                .record(duration);

        log.debug("알림 발송 시간 기록: priority={}, duration={}ms", priority, duration.toMillis());
    }

    /**
     * API 응답 시간 기록
     *
     * @param endpoint API 엔드포인트 (예: "/api/sites", "/api/alerts")
     * @param method   HTTP 메서드 (예: "GET", "POST")
     * @param duration 소요 시간
     */
    public void recordApiResponseTime(String endpoint, String method, Duration duration) {
        Timer.builder("webmonitor.api.response.time")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .description("API 응답 시간")
                .register(meterRegistry)
                .record(duration);

        log.debug("API 응답 시간 기록: endpoint={}, method={}, duration={}ms",
                endpoint, method, duration.toMillis());
    }

    // ========================================
    // UTILITY METHODS
    // ========================================

    /**
     * 예외 타입에서 간단한 이름 추출
     * 예: "java.net.SocketTimeoutException" → "SocketTimeoutException"
     *
     * @param exception 예외 객체
     * @return 예외 타입 간단 이름
     */
    public String getExceptionTypeName(Throwable exception) {
        return exception.getClass().getSimpleName();
    }

    /**
     * URL에서 호스트 추출 (메트릭 태그용)
     * 예: "https://www.example.com/page?query=1" → "www.example.com"
     *
     * @param url 전체 URL
     * @return 호스트 이름 (추출 실패 시 "unknown")
     */
    public String extractHostFromUrl(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost() != null ? uri.getHost() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
