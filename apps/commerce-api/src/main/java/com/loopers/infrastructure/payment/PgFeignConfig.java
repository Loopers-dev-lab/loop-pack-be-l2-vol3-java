package com.loopers.infrastructure.payment;

import feign.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class PgFeignConfig {

    @Bean
    public Request.Options pgFeignOptions() {
        return new Request.Options(
                1000, TimeUnit.MILLISECONDS,
                3000, TimeUnit.MILLISECONDS,
                true
        );
    }
}
