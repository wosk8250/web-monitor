package com.webmonitor.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MetricsService 단위 테스트")
class MetricsServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new MetricsService(meterRegistry);
    }

    // ========== Counter Tests ==========

    @Test
    @DisplayName("예외 카운터 증가 - 정상 케이스")
    void incrementExceptionCounter_incrementsCount() {
        metricsService.incrementExceptionCounter("SocketTimeoutException", "MonitorService");

        Counter counter = meterRegistry.find("webmonitor.exception.count")
                .tag("exception.type", "SocketTimeoutException")
                .tag("location", "MonitorService")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("예외 카운터 - 동일 태그 두 번 호출 시 누적")
    void incrementExceptionCounter_sameTagTwice_accumulates() {
        metricsService.incrementExceptionCounter("IOException", "AlertService");
        metricsService.incrementExceptionCounter("IOException", "AlertService");

        Counter counter = meterRegistry.find("webmonitor.exception.count")
                .tag("exception.type", "IOException")
                .counter();

        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("크롤링 성공 카운터 증가")
    void incrementCrawlSuccessCounter_incrementsCount() {
        metricsService.incrementCrawlSuccessCounter("https://example.com");

        Counter counter = meterRegistry.find("webmonitor.crawl.success")
                .tag("site.url", "https://example.com")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("크롤링 실패 카운터 증가")
    void incrementCrawlFailureCounter_incrementsCount() {
        metricsService.incrementCrawlFailureCounter("https://example.com", "timeout");

        Counter counter = meterRegistry.find("webmonitor.crawl.failure")
                .tag("site.url", "https://example.com")
                .tag("reason", "timeout")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("알림 발송 성공 카운터 증가")
    void incrementAlertSentCounter_incrementsCount() {
        metricsService.incrementAlertSentCounter("HIGH");

        Counter counter = meterRegistry.find("webmonitor.alert.sent")
                .tag("priority", "HIGH")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("알림 발송 실패 카운터 증가")
    void incrementAlertFailureCounter_incrementsCount() {
        metricsService.incrementAlertFailureCounter("NORMAL", "network_error");

        Counter counter = meterRegistry.find("webmonitor.alert.failure")
                .tag("priority", "NORMAL")
                .tag("reason", "network_error")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Thread Pool 거부 카운터 증가")
    void incrementThreadPoolRejectionCounter_incrementsCount() {
        metricsService.incrementThreadPoolRejectionCounter("alert");

        Counter counter = meterRegistry.find("webmonitor.threadpool.rejection")
                .tag("pool.name", "alert")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("키워드 매칭 카운터 증가")
    void incrementKeywordMatchCounter_incrementsCount() {
        metricsService.incrementKeywordMatchCounter("할인", "https://shop.com");

        Counter counter = meterRegistry.find("webmonitor.keyword.match")
                .tag("keyword", "할인")
                .tag("site.url", "https://shop.com")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    // ========== Gauge Tests ==========

    @Test
    @DisplayName("Thread Pool 활성 스레드 게이지 등록")
    void registerThreadPoolActiveThreadsGauge_registersGauge() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10));

        metricsService.registerThreadPoolActiveThreadsGauge("testPool", executor);

        Gauge gauge = meterRegistry.find("webmonitor.threadpool.active.threads")
                .tag("pool.name", "testPool")
                .gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);

        executor.shutdown();
    }

    @Test
    @DisplayName("Thread Pool 큐 사이즈 게이지 등록")
    void registerThreadPoolQueueSizeGauge_registersGauge() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10));

        metricsService.registerThreadPoolQueueSizeGauge("testPool", executor);

        Gauge gauge = meterRegistry.find("webmonitor.threadpool.queue.size")
                .tag("pool.name", "testPool")
                .gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(0.0);

        executor.shutdown();
    }

    @Test
    @DisplayName("Thread Pool 완료 작업 수 게이지 등록")
    void registerThreadPoolCompletedTasksGauge_registersGauge() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(10));

        metricsService.registerThreadPoolCompletedTasksGauge("testPool", executor);

        Gauge gauge = meterRegistry.find("webmonitor.threadpool.completed.tasks")
                .tag("pool.name", "testPool")
                .gauge();

        assertThat(gauge).isNotNull();

        executor.shutdown();
    }

    @Test
    @DisplayName("커스텀 게이지 등록 - 값 반영 확인")
    void registerCustomGauge_reflectsSupplierValue() {
        final double[] value = {42.0};
        metricsService.registerCustomGauge("custom.metric", "테스트 게이지", () -> value[0], "env", "test");

        Gauge gauge = meterRegistry.find("custom.metric").tag("env", "test").gauge();

        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(42.0);

        value[0] = 100.0;
        assertThat(gauge.value()).isEqualTo(100.0);
    }

    // ========== Timer Tests ==========

    @Test
    @DisplayName("크롤링 응답 시간 기록")
    void recordCrawlDuration_recordsTimer() {
        metricsService.recordCrawlDuration("https://example.com", Duration.ofMillis(500));

        Timer timer = meterRegistry.find("webmonitor.crawl.duration")
                .tag("site.url", "https://example.com")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("알림 발송 시간 기록")
    void recordAlertSendDuration_recordsTimer() {
        metricsService.recordAlertSendDuration("HIGH", Duration.ofMillis(200));

        Timer timer = meterRegistry.find("webmonitor.alert.send.duration")
                .tag("priority", "HIGH")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("API 응답 시간 기록")
    void recordApiResponseTime_recordsTimer() {
        metricsService.recordApiResponseTime("/api/sites", "GET", Duration.ofMillis(50));

        Timer timer = meterRegistry.find("webmonitor.api.response.time")
                .tag("endpoint", "/api/sites")
                .tag("method", "GET")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    // ========== Utility Tests ==========

    @Test
    @DisplayName("예외 타입 이름 추출 - 간단한 클래스명 반환")
    void getExceptionTypeName_returnsSimpleClassName() {
        RuntimeException ex = new RuntimeException("test");
        assertThat(metricsService.getExceptionTypeName(ex)).isEqualTo("RuntimeException");
    }

    @Test
    @DisplayName("URL에서 호스트 추출 - 정상 URL")
    void extractHostFromUrl_normalUrl_returnsHost() {
        String host = metricsService.extractHostFromUrl("https://www.example.com/path?q=1");
        assertThat(host).isEqualTo("www.example.com");
    }

    @Test
    @DisplayName("URL에서 호스트 추출 - 잘못된 URL은 unknown 반환")
    void extractHostFromUrl_malformedUrl_returnsUnknown() {
        String host = metricsService.extractHostFromUrl("not a valid url ::::");
        assertThat(host).isEqualTo("unknown");
    }
}
