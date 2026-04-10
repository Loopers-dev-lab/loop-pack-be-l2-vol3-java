package com.loopers.interfaces.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 대기열 스케줄러 전용 TaskScheduler.
 *
 * <p>기존 {@link SchedulerConfig}(poolSize=3)과 분리하여 100ms 주기의
 * 대기열 스케줄러가 기존 배치 스케줄러에 영향을 주지 않도록 한다.</p>
 *
 * <p>사용법: {@code @Scheduled} 대신 직접 {@code queueTaskScheduler.scheduleAtFixedRate()}
 * 또는 {@code @Scheduled(scheduler = "queueTaskScheduler")} (Spring 6.1+)</p>
 */
@Configuration
public class QueueSchedulerConfig {

    @Bean("queueTaskScheduler")
    public TaskScheduler queueTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("queue-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.initialize();
        return scheduler;
    }
}
