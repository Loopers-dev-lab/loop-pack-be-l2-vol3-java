package com.loopers.infrastructure.payment.toss;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;

@Configuration
public class TossClientConfig {

    @Bean("tossRestTemplate")
    public RestTemplate tossRestTemplate(RestTemplateBuilder builder, TossProperties tossProperties) {
        String credentials = tossProperties.secretKey() + ":";
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

        return builder
                .connectTimeout(Duration.ofMillis(tossProperties.connectTimeout()))
                .readTimeout(Duration.ofMillis(tossProperties.readTimeout()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .build();
    }
}
