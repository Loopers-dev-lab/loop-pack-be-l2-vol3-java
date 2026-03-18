package com.loopers.infrastructure.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.infrastructure.payment.pg.PgErrorDecoder;
import feign.Request;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class PgFeignConfig {

    @Bean
    public Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new PgErrorDecoder();
    }

    @Bean
    public Decoder decoder(ObjectMapper objectMapper) {
        return new PgResponseDecoder(objectMapper);
    }

    @Bean
    public Request.Options requestOptions(@Value("${pg.feign.read-timeout:2000}") long readTimeoutMs) {
        return new Request.Options(1000, TimeUnit.MILLISECONDS, readTimeoutMs, TimeUnit.MILLISECONDS, true);
    }
}
