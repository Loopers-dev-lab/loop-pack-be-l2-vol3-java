package com.loopers.support.config;

import com.loopers.infrastructure.pg.PgSimulatorProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate(RestTemplateBuilder builder, PgSimulatorProperties properties) {
        return builder
            .setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()))
            .build();
    }
}
