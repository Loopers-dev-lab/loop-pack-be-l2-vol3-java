package com.loopers.support.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30S")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(
            @Qualifier("masterRedisConnectionFactory") LettuceConnectionFactory connectionFactory
    ) {
        return new RedisLockProvider(connectionFactory);
    }
}
