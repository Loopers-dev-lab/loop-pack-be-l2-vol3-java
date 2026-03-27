package com.loopers.infrastructure.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 이벤트 메트릭 수집
 *
 * Prometheus로 노출되는 메트릭:
 * - outbox.pending.count: PENDING 이벤트 수
 * - outbox.processing.count: PROCESSING 이벤트 수
 * - outbox.published.count: PUBLISHED 이벤트 수
 * - outbox.failed.count: FAILED 이벤트 수
 * - outbox.oldest.pending.age.seconds: 가장 오래된 PENDING 이벤트 나이 (초)
 * - outbox.phase1.duration: Phase 1 처리 시간
 * - outbox.phase2.duration: Phase 2 처리 시간
 * - outbox.publish.success: 발행 성공 카운터
 * - outbox.publish.failed: 발행 실패 카운터
 */
@Component
public class OutboxMetrics {

    private final MeterRegistry registry;
    private final OutboxEventJpaRepository repository;

    // Counters
    private final Counter publishSuccessCounter;
    private final Counter publishFailedCounter;

    // Timers
    private final Timer phase1Timer;
    private final Timer phase2Timer;

    public OutboxMetrics(MeterRegistry registry, OutboxEventJpaRepository repository) {
        this.registry = registry;
        this.repository = repository;

        // Counters 초기화
        this.publishSuccessCounter = Counter.builder("outbox.publish.success")
                .description("Kafka 발행 성공 횟수")
                .register(registry);

        this.publishFailedCounter = Counter.builder("outbox.publish.failed")
                .description("Kafka 발행 실패 횟수")
                .register(registry);

        // Timers 초기화
        this.phase1Timer = Timer.builder("outbox.phase1.duration")
                .description("Phase 1 (PENDING → PROCESSING) 처리 시간")
                .register(registry);

        this.phase2Timer = Timer.builder("outbox.phase2.duration")
                .description("Phase 2 (PROCESSING → Kafka) 처리 시간")
                .register(registry);

        // Gauges 등록 (실시간 조회)
        registerGauges();
    }

    /**
     * Gauge 메트릭 등록
     * - 호출 시마다 DB 조회하여 실시간 값 반환
     */
    private void registerGauges() {
        // PENDING 이벤트 수
        registry.gauge("outbox.pending.count", this,
                metrics -> repository.countByStatus(OutboxStatus.PENDING));

        // PROCESSING 이벤트 수
        registry.gauge("outbox.processing.count", this,
                metrics -> repository.countByStatus(OutboxStatus.PROCESSING));

        // PUBLISHED 이벤트 수 (최근 1시간)
        registry.gauge("outbox.published.count", this,
                metrics -> repository.countByStatus(OutboxStatus.PUBLISHED));

        // FAILED 이벤트 수
        registry.gauge("outbox.failed.count", this,
                metrics -> repository.countByStatus(OutboxStatus.FAILED));

        // 가장 오래된 PENDING 이벤트 나이 (초)
        registry.gauge("outbox.oldest.pending.age.seconds", this,
                metrics -> calculateOldestPendingAge());
    }

    /**
     * 가장 오래된 PENDING 이벤트의 나이(초) 계산
     * - 0: PENDING 없음
     * - N: 가장 오래된 이벤트가 N초 전에 생성됨
     */
    private double calculateOldestPendingAge() {
        return repository.findOldestPendingEvent()
                .map(event -> {
                    Duration age = Duration.between(event.getCreatedAt(), ZonedDateTime.now());
                    return (double) age.getSeconds();
                })
                .orElse(0.0);
    }

    /**
     * Phase 1 처리 시간 기록
     */
    public void recordPhase1Duration(long durationMillis) {
        phase1Timer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Phase 2 처리 시간 기록
     */
    public void recordPhase2Duration(long durationMillis) {
        phase2Timer.record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 발행 성공 기록
     */
    public void recordPublishSuccess() {
        publishSuccessCounter.increment();
    }

    /**
     * 발행 실패 기록
     */
    public void recordPublishFailure() {
        publishFailedCounter.increment();
    }
}
