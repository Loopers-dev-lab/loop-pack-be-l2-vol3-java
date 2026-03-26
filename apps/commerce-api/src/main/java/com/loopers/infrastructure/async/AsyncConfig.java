package com.loopers.infrastructure.async;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 이벤트 리스너 전용 스레드풀.
     *
     * - corePoolSize=4: 평상시 유지 스레드
     * - maxPoolSize=8: 피크 시 최대 스레드
     * - queueCapacity=100: 대기 큐 (초과 시 CallerRunsPolicy → API 스레드에서 직접 실행)
     * - CallerRunsPolicy: 큐 포화 시 호출자 스레드에서 실행 (작업 유실 방지)
     */
    @Bean(name = "eventTaskExecutor")
    public Executor eventTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * @Async 메서드에서 발생한 미처리 예외를 로깅한다.
     * 이벤트 리스너 실패는 비즈니스 로직(주문, 좋아요)에 영향을 주지 않지만,
     * 로그가 없으면 문제 인지 자체가 불가능하다.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("[AsyncException] method={}, params={}", method.getName(), params, ex);
    }
}
