package com.loopers.support.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String EVENT_EXECUTOR = "eventExecutor";
    public static final String LOGGING_EXECUTOR = "loggingExecutor";

    private static final int EVENT_CORE_POOL_SIZE = 2;
    private static final int EVENT_MAX_POOL_SIZE = 4;
    private static final int EVENT_QUEUE_CAPACITY = 100;

    private static final int LOGGING_CORE_POOL_SIZE = 1;
    private static final int LOGGING_MAX_POOL_SIZE = 2;
    private static final int LOGGING_QUEUE_CAPACITY = 100;

    private static final int AWAIT_TERMINATION_SECONDS = 30;

    @Bean(name = EVENT_EXECUTOR)
    public Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(EVENT_CORE_POOL_SIZE);
        executor.setMaxPoolSize(EVENT_MAX_POOL_SIZE);
        executor.setQueueCapacity(EVENT_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    @Bean(name = LOGGING_EXECUTOR)
    public Executor loggingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(LOGGING_CORE_POOL_SIZE);
        executor.setMaxPoolSize(LOGGING_MAX_POOL_SIZE);
        executor.setQueueCapacity(LOGGING_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("logging-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }
}
