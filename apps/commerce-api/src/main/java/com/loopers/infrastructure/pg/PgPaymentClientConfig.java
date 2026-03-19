package com.loopers.infrastructure.pg;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class PgPaymentClientConfig {

    @Bean
    public Request.Options pgPaymentRequestOptions() {
        return new Request.Options(3, TimeUnit.SECONDS, 5, TimeUnit.SECONDS, true);
    }
}
