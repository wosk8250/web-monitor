package com.webmonitor.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 캐싱 설정
 * Caffeine 캐시를 사용하여 반복 조회 성능 향상
 *
 * application.properties 설정:
 * - spring.cache.type=caffeine
 * - spring.cache.caffeine.spec=maximumSize=500,expireAfterWrite=10m
 *
 * N+1 쿼리 최적화: 전역 키워드 조회 등 자주 조회되는 데이터 캐싱
 */
@Configuration
@EnableCaching
public class CacheConfig {
    // Caffeine cache는 application.properties에서 설정
    // 필요 시 @Bean으로 CacheManager 커스터마이징 가능
}
