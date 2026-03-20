package com.loopers.infrastructure.pg;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PgFeignConfig {

    @Bean
    public ErrorDecoder pgErrorDecoder() {
        return new PgFeignErrorDecoder();
    }
}
