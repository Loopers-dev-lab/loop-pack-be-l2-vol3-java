package com.loopers.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * PG 시뮬레이터 HTTP 클라이언트 설정
 *
 * 커넥션 타임아웃: PG에 연결하는 데 걸리는 시간 (짧게)
 * 리드 타임아웃: PG 응답을 기다리는 시간 (PG 처리 지연 고려)
 */
@Configuration
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
