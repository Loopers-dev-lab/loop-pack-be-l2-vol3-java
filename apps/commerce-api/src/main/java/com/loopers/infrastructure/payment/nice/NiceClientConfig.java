package com.loopers.infrastructure.payment.nice;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Base64;

@Configuration
public class NiceClientConfig {

    @Bean("niceRestTemplate")
    public RestTemplate niceRestTemplate(RestTemplateBuilder builder, NiceProperties niceProperties) {
        String credentials = niceProperties.clientKey() + ":" + niceProperties.secretKey();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());

        return builder
                .connectTimeout(Duration.ofMillis(niceProperties.connectTimeout()))
                .readTimeout(Duration.ofMillis(niceProperties.readTimeout()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .build();
    }
}
