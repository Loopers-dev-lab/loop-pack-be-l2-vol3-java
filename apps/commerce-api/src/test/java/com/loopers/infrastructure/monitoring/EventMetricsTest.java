package com.loopers.infrastructure.monitoring;

import com.loopers.domain.outbox.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EventMetrics 단위 테스트")
class EventMetricsTest {

    private MeterRegistry meterRegistry;
    private EventMetrics eventMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        when(outboxRepository.findPendingEvents(anyInt())).thenReturn(Collections.emptyList());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.initialize();

        eventMetrics = new EventMetrics(meterRegistry, outboxRepository, executor);
        eventMetrics.init();
    }

    @Test
    @DisplayName("incrementRedisFallback -- 카운터 증가 확인")
    void incrementRedisFallback_ShouldIncrement() {
        eventMetrics.incrementRedisFallback();
        eventMetrics.incrementRedisFallback();

        double count = meterRegistry.counter("coupon.redis.decr.fallback").count();
        assertThat(count).isEqualTo(2.0);
    }

    @Test
    @DisplayName("incrementIncrRestoreFail -- 카운터 증가 확인")
    void incrementIncrRestoreFail_ShouldIncrement() {
        eventMetrics.incrementIncrRestoreFail();

        double count = meterRegistry.counter("coupon.redis.incr.restore.fail").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    @DisplayName("스레드풀 게이지 등록 확인")
    void asyncThreadPoolGauge_ShouldBeRegistered() {
        assertThat(meterRegistry.find("async.threadpool.active").gauge()).isNotNull();
        assertThat(meterRegistry.find("async.threadpool.queue.size").gauge()).isNotNull();
        assertThat(meterRegistry.find("async.threadpool.pool.size").gauge()).isNotNull();
    }

    @Test
    @DisplayName("Outbox pending 게이지 등록 확인")
    void outboxPendingGauge_ShouldBeRegistered() {
        assertThat(meterRegistry.find("outbox.pending.count").gauge()).isNotNull();
    }
}
