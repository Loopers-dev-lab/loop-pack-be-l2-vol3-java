package com.loopers.infrastructure.monitoring;

import com.loopers.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * commerce-api 커스텀 Micrometer 메트릭.
 *
 * <p>Outbox 적체, @Async 스레드풀, 쿠폰 Redis 상태 등 핵심 메트릭을
 * Micrometer에 등록한다. Prometheus가 {@code /actuator/prometheus}에서 자동으로 수집한다.</p>
 */
@Component
public class EventMetrics {

    private final MeterRegistry meterRegistry;
    private final OutboxEventRepository outboxRepository;
    private final ThreadPoolTaskExecutor eventTaskExecutor;

    private Counter redisFallbackCounter;
    private Counter redisIncrRestoreFailCounter;

    public EventMetrics(
            MeterRegistry meterRegistry,
            OutboxEventRepository outboxRepository,
            @Qualifier("eventTaskExecutor") Executor eventTaskExecutor) {
        this.meterRegistry = meterRegistry;
        this.outboxRepository = outboxRepository;
        this.eventTaskExecutor = (ThreadPoolTaskExecutor) eventTaskExecutor;
    }

    @PostConstruct
    public void init() {
        // Outbox PENDING 이벤트 존재 여부 게이지
        Gauge.builder("outbox.pending.count", outboxRepository,
                repo -> repo.findPendingEvents(1).isEmpty() ? 0 : 1)
            .description("Outbox PENDING 이벤트 존재 여부")
            .register(meterRegistry);

        // @Async 스레드풀 메트릭
        Gauge.builder("async.threadpool.active", eventTaskExecutor,
                ThreadPoolTaskExecutor::getActiveCount)
            .description("이벤트 스레드풀 활성 스레드 수")
            .register(meterRegistry);

        Gauge.builder("async.threadpool.queue.size", eventTaskExecutor,
                executor -> executor.getThreadPoolExecutor().getQueue().size())
            .description("이벤트 스레드풀 대기 큐 크기")
            .register(meterRegistry);

        Gauge.builder("async.threadpool.pool.size", eventTaskExecutor,
                ThreadPoolTaskExecutor::getPoolSize)
            .description("이벤트 스레드풀 현재 풀 크기")
            .register(meterRegistry);

        // Redis 쿠폰 카운터
        redisFallbackCounter = Counter.builder("coupon.redis.decr.fallback")
            .description("Redis DECR 장애 fallback 횟수")
            .register(meterRegistry);

        redisIncrRestoreFailCounter = Counter.builder("coupon.redis.incr.restore.fail")
            .description("Redis INCR 복원 실패 횟수")
            .register(meterRegistry);
    }

    public void incrementRedisFallback() {
        redisFallbackCounter.increment();
    }

    public void incrementIncrRestoreFail() {
        redisIncrRestoreFailCounter.increment();
    }
}
