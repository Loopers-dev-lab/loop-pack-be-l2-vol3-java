package com.loopers.infrastructure.payment;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class PgClientConfig {

    @Bean("pgRestTemplate")
    public RestTemplate pgRestTemplate(RestTemplateBuilder builder, PgProperties pgProperties) {
        return builder
                .connectTimeout(Duration.ofMillis(pgProperties.connectTimeout()))
                .readTimeout(Duration.ofMillis(pgProperties.readTimeout()))
                .build();
    }
}
