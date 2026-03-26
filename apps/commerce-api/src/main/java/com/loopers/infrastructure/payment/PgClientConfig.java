package com.loopers.infrastructure.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * PG 통신용 RestTemplate Bean 설정.
 * <p>
 * Phase 3-1: connect/read timeout을 설정하여 PG 장애 시 무한 대기를 방지한다.
 * </p>
 */
@Configuration
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate(
            @Value("${pg.timeout.connect}") int connectTimeout,
            @Value("${pg.timeout.read}") int readTimeout) {
        // Phase 3-1: connect/read timeout 설정
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
