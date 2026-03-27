package com.loopers.infrastructure.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class PgFeignConfig {

    @Bean
    public ErrorDecoder pgErrorDecoder(ObjectMapper objectMapper) {
        return new PgErrorDecoder(objectMapper);
    }
}
