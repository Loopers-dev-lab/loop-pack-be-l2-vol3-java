package com.loopers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EnableScheduling
@Configuration
public class OutboxRelayConfig {

    @Bean(name = "outboxRelayExecutor", destroyMethod = "shutdown")
    public ExecutorService outboxRelayExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}
