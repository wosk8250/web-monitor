package com.webmonitor.actuator;

import com.webmonitor.service.CriticalErrorNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 커스텀 Health Indicator
 * Thread Pool의 상태를 모니터링하여 시스템 건강 상태를 체크합니다.
 * AsyncConfig의 Thread Pool이 존재할 때만 활성화됩니다.
 */
@Component
@ConditionalOnBean(name = {"alertExecutor", "productMonitorExecutor"})
@Slf4j
public class CustomHealthIndicator implements HealthIndicator {

    private final ThreadPoolTaskExecutor alertExecutor;
    private final ThreadPoolTaskExecutor productMonitorExecutor;
    private final Optional<CriticalErrorNotifier> criticalErrorNotifier;

    public CustomHealthIndicator(@Qualifier("alertExecutor") ThreadPoolTaskExecutor alertExecutor,
                                  @Qualifier("productMonitorExecutor") ThreadPoolTaskExecutor productMonitorExecutor,
                                  @Lazy @Autowired(required = false) CriticalErrorNotifier criticalErrorNotifier) {
        this.alertExecutor = alertExecutor;
        this.productMonitorExecutor = productMonitorExecutor;
        this.criticalErrorNotifier = Optional.ofNullable(criticalErrorNotifier);
    }

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();

            // Alert Thread Pool 상태 체크
            ThreadPoolStatus alertStatus = checkThreadPool("alert", alertExecutor);
            details.put("alertThreadPool", alertStatus.toMap());

            // ProductMonitor Thread Pool 상태 체크
            ThreadPoolStatus productMonitorStatus = checkThreadPool("productMonitor", productMonitorExecutor);
            details.put("productMonitorThreadPool", productMonitorStatus.toMap());

            // 전체 상태 판단
            if (alertStatus.isUnhealthy() || productMonitorStatus.isUnhealthy()) {
                // UNHEALTHY 상태: Critical 알림 발송
                notifyUnhealthyStatus(alertStatus, productMonitorStatus);
                return Health.down()
                        .withDetails(details)
                        .build();
            }

            if (alertStatus.isWarning() || productMonitorStatus.isWarning()) {
                // WARNING 상태: Warning 알림 발송
                notifyWarningStatus(alertStatus, productMonitorStatus);
                return Health.status("WARNING")
                        .withDetails(details)
                        .build();
            }

            return Health.up()
                    .withDetails(details)
                    .build();

        } catch (Exception e) {
            log.error("Health Check 실행 중 오류 발생", e);
            return Health.down()
                    .withException(e)
                    .build();
        }
    }

    /**
     * Thread Pool 상태 체크
     *
     * @param poolName Thread Pool 이름
     * @param executor ThreadPoolTaskExecutor
     * @return Thread Pool 상태
     */
    private ThreadPoolStatus checkThreadPool(String poolName, ThreadPoolTaskExecutor executor) {
        try {
            var threadPoolExecutor = executor.getThreadPoolExecutor();

            int activeCount = threadPoolExecutor.getActiveCount();
            int maxPoolSize = threadPoolExecutor.getMaximumPoolSize();
            int queueSize = threadPoolExecutor.getQueue().size();
            int queueCapacity = executor.getQueueCapacity();
            long completedTaskCount = threadPoolExecutor.getCompletedTaskCount();

            // 큐 사용률 계산
            double queueUsagePercent = queueCapacity > 0 ? (double) queueSize / queueCapacity * 100 : 0;

            // 스레드 사용률 계산
            double threadUsagePercent = maxPoolSize > 0 ? (double) activeCount / maxPoolSize * 100 : 0;

            String status;
            String message;

            // 상태 판단
            if (queueUsagePercent >= 90 || threadUsagePercent >= 100) {
                // 큐가 90% 이상 차있거나 스레드가 최대치 사용 중이면 UNHEALTHY
                status = "UNHEALTHY";
                message = String.format("%s Thread Pool이 포화 상태입니다 (큐: %.1f%%, 스레드: %.1f%%)",
                        poolName, queueUsagePercent, threadUsagePercent);
                log.warn(message);

            } else if (queueUsagePercent >= 70 || threadUsagePercent >= 80) {
                // 큐가 70% 이상 차있거나 스레드가 80% 이상 사용 중이면 WARNING
                status = "WARNING";
                message = String.format("%s Thread Pool 사용률이 높습니다 (큐: %.1f%%, 스레드: %.1f%%)",
                        poolName, queueUsagePercent, threadUsagePercent);
                log.debug(message);

            } else {
                // 정상
                status = "HEALTHY";
                message = String.format("%s Thread Pool이 정상 작동 중입니다", poolName);
            }

            return new ThreadPoolStatus(
                    status,
                    message,
                    activeCount,
                    maxPoolSize,
                    queueSize,
                    queueCapacity,
                    completedTaskCount,
                    queueUsagePercent,
                    threadUsagePercent
            );

        } catch (Exception e) {
            log.error("{} Thread Pool 상태 체크 실패", poolName, e);
            return new ThreadPoolStatus(
                    "ERROR",
                    String.format("%s Thread Pool 상태 체크 실패: %s", poolName, e.getMessage()),
                    0, 0, 0, 0, 0, 0, 0
            );
        }
    }

    /**
     * UNHEALTHY 상태 알림 전송
     */
    private void notifyUnhealthyStatus(ThreadPoolStatus alertStatus, ThreadPoolStatus productMonitorStatus) {
        criticalErrorNotifier.ifPresent(notifier -> {
            // Alert Pool이 UNHEALTHY인 경우
            if (alertStatus.isUnhealthy()) {
                notifier.notifyThreadPoolSaturation(
                        "Alert",
                        alertStatus.activeThreads,
                        alertStatus.maxThreads,
                        alertStatus.queueSize,
                        alertStatus.queueCapacity
                );
            }

            // ProductMonitor Pool이 UNHEALTHY인 경우
            if (productMonitorStatus.isUnhealthy()) {
                notifier.notifyThreadPoolSaturation(
                        "ProductMonitor",
                        productMonitorStatus.activeThreads,
                        productMonitorStatus.maxThreads,
                        productMonitorStatus.queueSize,
                        productMonitorStatus.queueCapacity
                );
            }
        });
    }

    /**
     * WARNING 상태 알림 전송
     */
    private void notifyWarningStatus(ThreadPoolStatus alertStatus, ThreadPoolStatus productMonitorStatus) {
        criticalErrorNotifier.ifPresent(notifier -> {
            Map<String, String> details = new HashMap<>();

            if (alertStatus.isWarning()) {
                details.put("Alert Pool 스레드 사용률",
                        String.format("%.1f%% (%d / %d)", alertStatus.threadUsagePercent,
                                alertStatus.activeThreads, alertStatus.maxThreads));
                details.put("Alert Pool 큐 사용률",
                        String.format("%.1f%% (%d / %d)", alertStatus.queueUsagePercent,
                                alertStatus.queueSize, alertStatus.queueCapacity));
            }

            if (productMonitorStatus.isWarning()) {
                details.put("ProductMonitor Pool 스레드 사용률",
                        String.format("%.1f%% (%d / %d)", productMonitorStatus.threadUsagePercent,
                                productMonitorStatus.activeThreads, productMonitorStatus.maxThreads));
                details.put("ProductMonitor Pool 큐 사용률",
                        String.format("%.1f%% (%d / %d)", productMonitorStatus.queueUsagePercent,
                                productMonitorStatus.queueSize, productMonitorStatus.queueCapacity));
            }

            notifier.sendCriticalNotification(
                    "Thread Pool 사용률 경고",
                    "Thread Pool 사용률이 높습니다. 모니터링이 필요합니다.",
                    CriticalErrorNotifier.NotificationLevel.WARNING,
                    details
            );
        });
    }

    /**
     * Thread Pool 상태 DTO
     */
    private static class ThreadPoolStatus {
        private final String status;
        private final String message;
        private final int activeThreads;
        private final int maxThreads;
        private final int queueSize;
        private final int queueCapacity;
        private final long completedTasks;
        private final double queueUsagePercent;
        private final double threadUsagePercent;

        public ThreadPoolStatus(String status, String message,
                                 int activeThreads, int maxThreads,
                                 int queueSize, int queueCapacity,
                                 long completedTasks,
                                 double queueUsagePercent, double threadUsagePercent) {
            this.status = status;
            this.message = message;
            this.activeThreads = activeThreads;
            this.maxThreads = maxThreads;
            this.queueSize = queueSize;
            this.queueCapacity = queueCapacity;
            this.completedTasks = completedTasks;
            this.queueUsagePercent = queueUsagePercent;
            this.threadUsagePercent = threadUsagePercent;
        }

        public boolean isUnhealthy() {
            return "UNHEALTHY".equals(status) || "ERROR".equals(status);
        }

        public boolean isWarning() {
            return "WARNING".equals(status);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("status", status);
            map.put("message", message);
            map.put("activeThreads", activeThreads);
            map.put("maxThreads", maxThreads);
            map.put("queueSize", queueSize);
            map.put("queueCapacity", queueCapacity);
            map.put("completedTasks", completedTasks);
            map.put("queueUsagePercent", String.format("%.1f%%", queueUsagePercent));
            map.put("threadUsagePercent", String.format("%.1f%%", threadUsagePercent));
            return map;
        }
    }
}
