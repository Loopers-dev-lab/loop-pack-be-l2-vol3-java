package com.loopers.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties(PgProperties.class)
public class PgClientConfig {

    @Bean
    public RestTemplate pgRestTemplate(PgProperties pgProperties) {
        return new RestTemplateBuilder()
            .rootUri(pgProperties.baseUrl())
            .setConnectTimeout(Duration.ofMillis(pgProperties.timeout().connect()))
            .setReadTimeout(Duration.ofMillis(pgProperties.timeout().read()))
            .build();
    }
}
