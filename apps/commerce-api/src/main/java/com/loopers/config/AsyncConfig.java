package com.loopers.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 스레드 풀 미리 생성 — SimpleAsyncTaskExecutor의 요청마다 스레드 생성 비용 제거
        executor.setCorePoolSize(20);
        // 부하 급증 시 최대 100개까지 확장
        executor.setMaxPoolSize(100);
        // 풀 포화 시 최대 500개까지 큐잉 (드롭 없음)
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        // 앱 종료 시 큐에 남은 작업(토큰 삭제 등) 완료 후 종료
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
