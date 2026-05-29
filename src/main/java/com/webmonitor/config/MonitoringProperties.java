package com.webmonitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 모니터링 관련 설정값을 외부화하는 Properties 클래스
 */
@Configuration
@ConfigurationProperties(prefix = "monitor")
@Data
public class MonitoringProperties {

    private RateLimitProperties rateLimit = new RateLimitProperties();
    private FailureProperties failure = new FailureProperties();
    private RestockProperties restock = new RestockProperties();

    /**
     * Rate Limiting 설정
     */
    @Data
    public static class RateLimitProperties {
        /**
         * 최소 딜레이 (밀리초)
         */
        private long minDelayMs = 2000;

        /**
         * 최대 딜레이 (밀리초)
         */
        private long maxDelayMs = 5000;
    }

    /**
     * 실패 처리 설정
     */
    @Data
    public static class FailureProperties {
        /**
         * 연속 실패 알림 임계값
         */
        private int alertThreshold = 5;

        /**
         * 성공 시 실패 카운터 초기화 여부
         */
        private boolean resetAfterSuccess = true;
    }

    /**
     * 재입고 알림 설정
     */
    @Data
    public static class RestockProperties {
        /**
         * 재입고 알림 쿨다운 (분)
         */
        private int alertCooldownMinutes = 60;
    }
}
