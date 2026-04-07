package com.loopers.application.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 상품 조회(읽기) 경로에서 Outbox 적재를 비동기로 분리하기 위한 실행기.
 */
@Configuration
@EnableAsync
public class ProductViewOutboxAsyncConfig {

    @Bean(name = "productViewOutboxExecutor")
    public Executor productViewOutboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("product-view-outbox-");
        executor.initialize();
        return executor;
    }
}
