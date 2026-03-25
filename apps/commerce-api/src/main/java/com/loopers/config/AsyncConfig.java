package com.loopers.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 비동기 + 스케줄링 설정
 *
 * @EnableAsync: @Async 이벤트 리스너용
 * @EnableScheduling: Outbox Relay Polling 스케줄러용 (@Scheduled)
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
