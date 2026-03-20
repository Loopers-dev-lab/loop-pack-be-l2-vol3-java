package com.loopers.infrastructure.pg;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class PgFeignConfig {

    @Bean
    public ErrorDecoder pgErrorDecoder() {
        return new PgFeignErrorDecoder();
    }
}
