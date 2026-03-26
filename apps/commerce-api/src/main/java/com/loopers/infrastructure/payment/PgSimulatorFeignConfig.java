package com.loopers.infrastructure.payment;

import feign.Request;
import feign.RequestInterceptor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class PgSimulatorFeignConfig {

    @Value("${pg.simulator.user-id:135135}")
    private String userId;

    @Value("${pg.simulator.timeout.connect-ms:3000}")
    private int connectTimeoutMs;

    @Value("${pg.simulator.timeout.read-ms:5000}")
    private int readTimeoutMs;

    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(connectTimeoutMs, TimeUnit.MILLISECONDS, readTimeoutMs, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public RequestInterceptor pgUserIdInterceptor() {
        return requestTemplate -> requestTemplate.header("X-USER-ID", userId);
    }
}
