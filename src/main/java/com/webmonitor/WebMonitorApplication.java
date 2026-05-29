package com.webmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 스케줄링 기능 활성화
@EnableCaching    // 캐싱 기능 활성화 (성능 최적화)
public class WebMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebMonitorApplication.class, args);
    }

}
