package com.loopers.infrastructure.pg;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class PgQueryFeignConfig {

    @Bean
    public Request.Options pgQueryFeignOptions() {
        return new Request.Options(
                2, TimeUnit.SECONDS,
                2, TimeUnit.SECONDS,   // 실험1 도출: p99(500ms) × 4배 안전 마진
                true
        );
    }
}
