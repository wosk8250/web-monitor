package com.webmonitor.config;

import com.webmonitor.event.CriticalErrorEvent;
import com.webmonitor.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 비동기 처리 설정
 * 1. 제품 모니터링을 병렬로 처리하기 위한 Thread Pool 설정
 * 2. 재고 알림 즉시 발송을 위한 Thread Pool 설정
 * 3. @Async 메서드의 예외 처리
 *
 * 순환 의존성 해결: ApplicationEvent 기반으로 CriticalErrorNotifier와 분리
 */
@Configuration
@EnableAsync
@RequiredArgsConstructor
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    private final MetricsService metricsService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 재고 알림 즉시 발송용 Thread Pool 설정
     *
     * - corePoolSize: 기본 스레드 수 (10개, I/O 작업용 - 트래픽 폭증 대비)
     * - maxPoolSize: 최대 스레드 수 (50개, 대량 알림 발송 대비)
     * - queueCapacity: 대기 큐 크기 (200개, 충분한 버퍼)
     * - threadNamePrefix: 스레드 이름 접두사
     *
     * HIGH 우선순위 알림(재고 알림)을 즉시 발송
     * Discord API는 I/O bound이므로 스레드 많이 필요
     *
     * 코드 리뷰 개선: 트래픽 폭증 시 작업 거부 방지 위해 Thread Pool 증설
     */
    @Bean(name = "alertExecutor")
    public Executor alertExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 기본 스레드 수 (3 → 10으로 증가)
        executor.setCorePoolSize(10);

        // 최대 스레드 수 (10 → 50으로 증가)
        executor.setMaxPoolSize(50);

        // 대기 큐 크기 (50 → 200으로 증가, 충분한 버퍼)
        executor.setQueueCapacity(200);

        // 거부 정책: 큐 초과 시 로깅 후 버림 (스케줄러 블로킹 방지)
        executor.setRejectedExecutionHandler((runnable, exec) -> {
            log.warn("Alert Thread Pool 큐 초과 - 작업 거부됨 (Pool: {}, Queue: {}/{})",
                    exec.getActiveCount(), exec.getQueue().size(), executor.getQueueCapacity());

            // Critical 알림 발송 (이벤트 발행)
            eventPublisher.publishEvent(new CriticalErrorEvent(
                    this,
                    "ThreadPoolRejection",
                    "AsyncConfig.alertExecutor"
            ));
        });

        // Thread별 예외 핸들러 설정
        executor.setThreadFactory(r -> {
            Thread thread = new Thread(r);
            thread.setName("Alert-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("==================================================");
                log.error("Alert Thread Pool에서 Uncaught Exception 발생!");
                log.error("Thread: {}", t.getName());
                log.error("Exception 타입: {}", e.getClass().getName());
                log.error("Exception 메시지: {}", e.getMessage());
                log.error("Stack Trace:", e);
                log.error("==================================================");

                // 메트릭 기록: Alert Thread Pool에서 발생한 예외
                metricsService.incrementExceptionCounter(
                        e.getClass().getSimpleName(),
                        "AsyncConfig.alertExecutor"
                );
            });
            return thread;
        });

        // 종료 대기 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        // Thread Pool 메트릭 등록
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        metricsService.registerThreadPoolActiveThreadsGauge("alert", threadPoolExecutor);
        metricsService.registerThreadPoolQueueSizeGauge("alert", threadPoolExecutor);
        metricsService.registerThreadPoolCompletedTasksGauge("alert", threadPoolExecutor);

        log.info("Alert Thread Pool 초기화 완료 (Core: {}, Max: {}, Queue: {})",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * 제품 모니터링용 Thread Pool 설정
     *
     * - corePoolSize: 기본 스레드 수 (15개, HTTP 요청 I/O 대기용)
     * - maxPoolSize: 최대 스레드 수 (30개, 대량 제품 모니터링 대비)
     * - queueCapacity: 대기 큐 크기 (100개, 충분한 버퍼)
     * - threadNamePrefix: 스레드 이름 접두사
     *
     * 외부 HTTP 요청이 대부분의 시간 소비 (I/O bound) → 스레드 많이 필요
     *
     * 코드 리뷰 개선: 트래픽 폭증 시 작업 거부 방지 위해 Thread Pool 증설
     */
    @Bean(name = "productMonitorExecutor")
    public Executor productMonitorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 기본 스레드 수 (10 → 15로 증가)
        executor.setCorePoolSize(15);

        // 최대 스레드 수 (20 → 30으로 증가)
        executor.setMaxPoolSize(30);

        // 대기 큐 크기 (50 → 100으로 증가, 충분한 버퍼)
        executor.setQueueCapacity(100);

        // 거부 정책: 큐 초과 시 로깅 후 버림 (스케줄러 블로킹 방지)
        executor.setRejectedExecutionHandler((runnable, exec) -> {
            log.warn("ProductMonitor Thread Pool 큐 초과 - 작업 거부됨 (Pool: {}, Queue: {}/{})",
                    exec.getActiveCount(), exec.getQueue().size(), executor.getQueueCapacity());

            // Critical 알림 발송 (이벤트 발행)
            eventPublisher.publishEvent(new CriticalErrorEvent(
                    this,
                    "ThreadPoolRejection",
                    "AsyncConfig.productMonitorExecutor"
            ));
        });

        // Thread별 예외 핸들러 설정
        executor.setThreadFactory(r -> {
            Thread thread = new Thread(r);
            thread.setName("ProductMonitor-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("==================================================");
                log.error("ProductMonitor Thread Pool에서 Uncaught Exception 발생!");
                log.error("Thread: {}", t.getName());
                log.error("Exception 타입: {}", e.getClass().getName());
                log.error("Exception 메시지: {}", e.getMessage());
                log.error("Stack Trace:", e);
                log.error("==================================================");

                // 메트릭 기록: ProductMonitor Thread Pool에서 발생한 예외
                metricsService.incrementExceptionCounter(
                        e.getClass().getSimpleName(),
                        "AsyncConfig.productMonitorExecutor"
                );
            });
            return thread;
        });

        // 종료 대기 설정
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        // Thread Pool 메트릭 등록
        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        metricsService.registerThreadPoolActiveThreadsGauge("productMonitor", threadPoolExecutor);
        metricsService.registerThreadPoolQueueSizeGauge("productMonitor", threadPoolExecutor);
        metricsService.registerThreadPoolCompletedTasksGauge("productMonitor", threadPoolExecutor);

        log.info("ProductMonitor Thread Pool 초기화 완료 (Core: {}, Max: {}, Queue: {})",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }

    /**
     * 사이트 모니터링 병렬 실행용 Thread Pool
     * 사이트 크롤링은 I/O bound — 동시에 여러 사이트를 크롤링해 순차 루프 병목 해소
     */
    @Bean(name = "siteMonitorExecutor")
    public Executor siteMonitorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);

        executor.setRejectedExecutionHandler((runnable, exec) -> {
            log.warn("SiteMonitor Thread Pool 큐 초과 - 작업 거부됨 (Pool: {}, Queue: {}/{})",
                    exec.getActiveCount(), exec.getQueue().size(), executor.getQueueCapacity());
            eventPublisher.publishEvent(new CriticalErrorEvent(
                    this, "ThreadPoolRejection", "AsyncConfig.siteMonitorExecutor"));
        });

        executor.setThreadFactory(r -> {
            Thread thread = new Thread(r);
            thread.setName("SiteMonitor-" + thread.getId());
            thread.setUncaughtExceptionHandler((t, e) -> {
                log.error("==================================================");
                log.error("SiteMonitor Thread Pool에서 Uncaught Exception 발생!");
                log.error("Thread: {}", t.getName());
                log.error("Exception 타입: {}", e.getClass().getName());
                log.error("Exception 메시지: {}", e.getMessage());
                log.error("Stack Trace:", e);
                log.error("==================================================");
                metricsService.incrementExceptionCounter(
                        e.getClass().getSimpleName(), "AsyncConfig.siteMonitorExecutor");
            });
            return thread;
        });

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        ThreadPoolExecutor threadPoolExecutor = executor.getThreadPoolExecutor();
        metricsService.registerThreadPoolActiveThreadsGauge("siteMonitor", threadPoolExecutor);
        metricsService.registerThreadPoolQueueSizeGauge("siteMonitor", threadPoolExecutor);
        metricsService.registerThreadPoolCompletedTasksGauge("siteMonitor", threadPoolExecutor);

        log.info("SiteMonitor Thread Pool 초기화 완료 (Core: {}, Max: {}, Queue: {})",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * @Async 메서드에서 발생한 미처리 예외 핸들러
     * 비동기 메서드에서 발생한 예외는 호출자에게 전파되지 않으므로 여기서 로깅
     */
    @Override
    @NonNull
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncUncaughtExceptionHandler() {
            @Override
            public void handleUncaughtException(
                    @NonNull Throwable throwable,
                    @NonNull Method method,
                    @NonNull Object... params) {

                // 상세 정보 로깅
                log.error("==================================================");
                log.error("@Async 메서드 실행 중 예외 발생!");
                log.error("메서드: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());
                log.error("파라미터 개수: {}", params.length);

                // 파라미터 로깅 (최대 3개까지, 보안을 위해 제한)
                for (int i = 0; i < Math.min(params.length, 3); i++) {
                    if (params[i] != null) {
                        log.error("파라미터 [{}]: {} = {}",
                                i,
                                params[i].getClass().getSimpleName(),
                                params[i].toString().length() > 100 ?
                                        params[i].toString().substring(0, 100) + "..." :
                                        params[i].toString());
                    } else {
                        log.error("파라미터 [{}]: null", i);
                    }
                }

                log.error("예외 타입: {}", throwable.getClass().getName());
                log.error("예외 메시지: {}", throwable.getMessage());
                log.error("스택 트레이스:", throwable);
                log.error("==================================================");

                // 메트릭 기록: @Async 메서드에서 발생한 예외
                metricsService.incrementExceptionCounter(
                        throwable.getClass().getSimpleName(),
                        "AsyncConfig.@Async." + method.getDeclaringClass().getSimpleName() + "." + method.getName()
                );
            }
        };
    }
}
