package com.webmonitor.service;

import com.webmonitor.event.CriticalErrorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 중요 시스템 오류 알림 서비스
 * Thread Pool 포화, Health Check 실패, 연속 예외 등 critical 상황을 즉시 알림
 * RestTemplate Bean 주입: Connection Pooling을 통한 성능 최적화
 *
 * 순환 의존성 해결: ApplicationEvent 기반으로 GlobalExceptionHandler와 분리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriticalErrorNotifier {

    private final RestTemplate restTemplate;
    private final MetricsService metricsService;

    @Value("${discord.webhook.url:}")
    private String discordWebhookUrl;

    @Value("${critical.notification.enabled:true}")
    private boolean notificationEnabled;

    @Value("${critical.notification.cooldown-minutes:15}")
    private int cooldownMinutes;

    @Value("${critical.notification.exception-threshold:10}")
    private int exceptionThreshold;

    @Value("${critical.notification.exception-window-minutes:5}")
    private int exceptionWindowMinutes;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 알림 쿨다운 관리 (키: 알림 유형, 값: 마지막 전송 시간)
    private final Map<String, LocalDateTime> lastNotificationTime = new ConcurrentHashMap<>();

    // 예외 추적 (키: 예외 타입+위치, 값: 발생 시간 리스트)
    private final Map<String, List<LocalDateTime>> exceptionTracker = new ConcurrentHashMap<>();

    /**
     * 알림 레벨
     */
    public enum NotificationLevel {
        CRITICAL(15158332),  // 빨강 (RGB: 231, 76, 60)
        WARNING(16776960),   // 노랑 (RGB: 255, 255, 0)
        INFO(3447003);       // 파랑 (RGB: 52, 120, 243)

        private final int color;

        NotificationLevel(int color) {
            this.color = color;
        }

        public int getColor() {
            return color;
        }
    }

    /**
     * Critical 알림 발송
     *
     * @param title       알림 제목
     * @param description 알림 상세 내용
     * @param level       알림 레벨
     * @param fields      추가 필드 (키-값 쌍)
     */
    public void sendCriticalNotification(String title, String description, NotificationLevel level, Map<String, String> fields) {
        if (!notificationEnabled) {
            log.debug("Critical 알림이 비활성화되어 있습니다.");
            return;
        }

        if (discordWebhookUrl == null || discordWebhookUrl.trim().isEmpty()) {
            log.debug("Discord Webhook URL이 설정되지 않아 알림을 건너뜁니다.");
            return;
        }

        // 쿨다운 체크
        String notificationKey = title + "_" + level.name();
        if (!canSendNotification(notificationKey)) {
            log.debug("알림 쿨다운 기간 중입니다. 알림을 건너뜁니다: {}", title);
            return;
        }

        try {
            // Discord Embed 형식으로 메시지 구성
            Map<String, Object> embed = new HashMap<>();
            embed.put("title", String.format("[%s] %s", level.name(), title));
            embed.put("description", description);
            embed.put("color", level.getColor());
            embed.put("timestamp", LocalDateTime.now().toString());

            // 필드 추가
            if (fields != null && !fields.isEmpty()) {
                List<Map<String, Object>> embedFields = fields.entrySet().stream()
                        .map(entry -> {
                            Map<String, Object> field = new HashMap<>();
                            field.put("name", entry.getKey());
                            field.put("value", entry.getValue());
                            field.put("inline", true);
                            return field;
                        })
                        .toList();
                embed.put("fields", embedFields);
            }

            // Footer 추가
            Map<String, Object> footer = new HashMap<>();
            footer.put("text", "Web Monitor - Critical Alert System");
            embed.put("footer", footer);

            // 최종 요청 바디 구성
            Map<String, Object> body = new HashMap<>();
            body.put("embeds", List.of(embed));

            // HTTP 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            // Discord Webhook 호출
            ResponseEntity<String> response = restTemplate.postForEntity(discordWebhookUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Critical 알림 전송 성공: {} (레벨: {})", title, level);
                updateLastNotificationTime(notificationKey);
                metricsService.incrementAlertSentCounter("CRITICAL_" + level.name());
            } else {
                log.warn("Critical 알림 전송 실패: HTTP {}", response.getStatusCode().value());
                metricsService.incrementAlertFailureCounter("CRITICAL_" + level.name(), "HTTP_" + response.getStatusCode().value());
            }

        } catch (Exception e) {
            log.error("Critical 알림 전송 중 오류 발생: {}", title, e);
            metricsService.incrementAlertFailureCounter("CRITICAL_" + level.name(), "EXCEPTION");
        }
    }

    /**
     * Thread Pool 포화 알림
     *
     * @param poolName Thread Pool 이름
     * @param activeThreads 활성 스레드 수
     * @param maxThreads 최대 스레드 수
     * @param queueSize 큐 사이즈
     * @param queueCapacity 큐 용량
     */
    public void notifyThreadPoolSaturation(String poolName, int activeThreads, int maxThreads, int queueSize, int queueCapacity) {
        Map<String, String> fields = new HashMap<>();
        fields.put("Thread Pool", poolName);
        fields.put("활성 스레드", String.format("%d / %d (%.1f%%)", activeThreads, maxThreads, (double) activeThreads / maxThreads * 100));
        fields.put("큐 사용률", String.format("%d / %d (%.1f%%)", queueSize, queueCapacity, (double) queueSize / queueCapacity * 100));
        fields.put("시간", LocalDateTime.now().format(TIME_FORMATTER));

        sendCriticalNotification(
                "Thread Pool 포화 경고",
                String.format("%s Thread Pool이 용량 한계에 도달했습니다. 시스템 성능이 저하될 수 있습니다.", poolName),
                NotificationLevel.CRITICAL,
                fields
        );
    }

    /**
     * Health Check 실패 알림
     *
     * @param healthStatus 건강 상태 (DOWN, WARNING 등)
     * @param details 상세 정보
     */
    public void notifyHealthCheckFailure(String healthStatus, Map<String, String> details) {
        NotificationLevel level = "DOWN".equals(healthStatus) ? NotificationLevel.CRITICAL : NotificationLevel.WARNING;

        sendCriticalNotification(
                "시스템 Health Check 실패",
                String.format("시스템 건강 상태가 %s입니다. 즉시 확인이 필요합니다.", healthStatus),
                level,
                details
        );
    }

    /**
     * CriticalErrorEvent 수신 (이벤트 기반 알림)
     * GlobalExceptionHandler에서 발행한 이벤트를 수신하여 처리
     *
     * @param event Critical 오류 이벤트
     */
    @EventListener
    public void onCriticalErrorEvent(CriticalErrorEvent event) {
        trackExceptionAndNotify(event.getExceptionType(), event.getLocation());
    }

    /**
     * 연속 예외 발생 추적 및 알림
     *
     * @param exceptionType 예외 타입
     * @param location 발생 위치
     */
    public void trackExceptionAndNotify(String exceptionType, String location) {
        String key = exceptionType + "@" + location;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusMinutes(exceptionWindowMinutes);

        // 예외 기록 추가
        exceptionTracker.putIfAbsent(key, new java.util.concurrent.CopyOnWriteArrayList<>());
        List<LocalDateTime> occurrences = exceptionTracker.get(key);
        occurrences.add(now);

        // 윈도우 밖의 오래된 기록 제거
        occurrences.removeIf(time -> time.isBefore(windowStart));

        // 임계값 초과 시 알림
        if (occurrences.size() >= exceptionThreshold) {
            Map<String, String> fields = new HashMap<>();
            fields.put("예외 타입", exceptionType);
            fields.put("발생 위치", location);
            fields.put("발생 횟수", String.format("%d회 / %d분", occurrences.size(), exceptionWindowMinutes));
            fields.put("임계값", String.format("%d회", exceptionThreshold));
            fields.put("시간", now.format(TIME_FORMATTER));

            sendCriticalNotification(
                    "연속 예외 발생 경고",
                    String.format("%s 예외가 짧은 시간 내에 반복적으로 발생하고 있습니다.", exceptionType),
                    NotificationLevel.CRITICAL,
                    fields
            );

            // 알림 후 카운터 초기화 (중복 알림 방지)
            occurrences.clear();
        }
    }

    /**
     * 쿨다운 체크: 같은 알림을 너무 자주 보내지 않도록 제한
     */
    private boolean canSendNotification(String notificationKey) {
        LocalDateTime lastSent = lastNotificationTime.get(notificationKey);
        if (lastSent == null) {
            return true;
        }

        LocalDateTime cooldownExpiry = lastSent.plusMinutes(cooldownMinutes);
        return LocalDateTime.now().isAfter(cooldownExpiry);
    }

    /**
     * 마지막 알림 시간 업데이트
     */
    private void updateLastNotificationTime(String notificationKey) {
        lastNotificationTime.put(notificationKey, LocalDateTime.now());
    }

    /**
     * 단순 메시지 알림 (간편 메서드)
     */
    public void sendSimpleNotification(String title, String message, NotificationLevel level) {
        sendCriticalNotification(title, message, level, null);
    }
}
