package com.loopers.infrastructure.payment;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class PgClientConfig {

    @Bean
    public RestClient pgRestClient() {
        return RestClient.builder()
            .baseUrl("http://localhost:8082")
            .requestFactory(ClientHttpRequestFactories.get(
                ClientHttpRequestFactorySettings.DEFAULTS
                    .withConnectTimeout(Duration.ofSeconds(2))
                    .withReadTimeout(Duration.ofSeconds(5))
            ))
            .build();
    }
}
