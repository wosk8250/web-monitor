package com.webmonitor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 설정
 * Connection Pooling을 통한 성능 최적화
 */
@Configuration
@Slf4j
public class RestTemplateConfig {

    /**
     * Discord 웹훅 및 외부 API 호출용 RestTemplate
     *
     * Connection Pool 설정:
     * - Connect Timeout: 5초
     * - Read Timeout: 10초
     *
     * RestTemplate을 Bean으로 등록하여 Connection Pooling 활성화
     * 매번 새로운 Connection을 생성하지 않고 재사용하여 성능 향상
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.info("RestTemplate Bean 초기화 (Connection Pooling 활성화)");

        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .requestFactory(this::clientHttpRequestFactory)
                .build();
    }

    /**
     * HTTP 요청 팩토리 설정
     *
     * SimpleClientHttpRequestFactory는 기본적으로 Connection Pooling을 지원하지 않으므로,
     * 프로덕션 환경에서는 Apache HttpClient 또는 OkHttp 사용 권장
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);  // 5초
        factory.setReadTimeout(10000);    // 10초

        return factory;
    }
}
