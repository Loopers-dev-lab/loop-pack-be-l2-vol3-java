package com.loopers.interfaces.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 스케줄러 스레드 풀 설정.
 * <p>
 * Spring {@code @Scheduled}의 기본 단일 스레드 대신 별도 풀을 사용한다.
 * PaymentPollingScheduler와 OrderExpiryScheduler가 서로 독립적으로 실행되어,
 * 한 쪽 지연이 다른 쪽에 전파되지 않는다.
 * </p>
 */
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("batch-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        registrar.setTaskScheduler(scheduler);
    }
}
