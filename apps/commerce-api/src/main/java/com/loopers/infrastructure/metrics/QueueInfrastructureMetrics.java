package com.loopers.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 대기열·저장소 관련 관측 카운터를 한 빈에서 등록한다.
 * <p>
 * <b>분리 이유</b>: Micrometer 미터 이름·설명을 API 계층({@link com.loopers.interfaces.api.ApiControllerAdvice}),
 * Kafka 발행({@link com.loopers.infrastructure.queue.KafkaQueueJoinFallbackPublisher}),
 * Kafka 소비({@link com.loopers.infrastructure.queue.QueueJoinFallbackKafkaListener})에 걸쳐 동일하게 유지하고,
 * 각 컴포넌트는 카운터 증가만 호출하도록 하기 위함이다. 발행/리스너 안에 카운터를 흩뿌리면 이름 불일치·중복 등록 위험이 있다.
 */
@Component
public class QueueInfrastructureMetrics {

    private final Counter apiBackendFailures;
    private final Counter kafkaJoinFallbackPublished;
    private final Counter kafkaJoinFallbackPublishFailed;
    private final Counter kafkaJoinFallbackRecovered;
    private final Counter kafkaJoinFallbackDlt;

    /**
     /**
      * @param meterRegistry 미터(Meter) 및 메트릭(Metrics)을 관리·등록하는 Micrometer의 중앙 저장소 객체
      *                      각종 카운터·게이지·타이머 등의 지표를 여기에 등록하면,
      *                      Spring Boot Actuator, Prometheus 등 외부 시스템에서 수집할 수 있다.
      */
    public QueueInfrastructureMetrics(MeterRegistry meterRegistry) {
        this.apiBackendFailures = Counter.builder("loopers.queue.backend.failures")
                .description("API에서 저장소(Redis/DB) 일시 장애로 매핑된 횟수")
                .tag("layer", "api")
                .register(meterRegistry);
        this.kafkaJoinFallbackPublished = Counter.builder("loopers.queue.join.fallback.kafka.published")
                .description("Redis 장애 시 대기열 진입 의도를 Kafka로 발행한 횟수")
                .register(meterRegistry);
        this.kafkaJoinFallbackPublishFailed = Counter.builder("loopers.queue.join.fallback.kafka.publish.failed")
                .description("대기열 Kafka 폴백 발행 실패 횟수")
                .register(meterRegistry);
        this.kafkaJoinFallbackRecovered = Counter.builder("loopers.queue.join.fallback.recovered")
                .description("Kafka 폴백 메시지 처리로 Redis 대기열에 반영한 횟수")
                .register(meterRegistry);
        this.kafkaJoinFallbackDlt = Counter.builder("loopers.queue.join.fallback.dlt")
                .description("대기열 Kafka 폴백 소비 실패로 DLT에 전달된 횟수")
                .register(meterRegistry);
    }

    /** 컨트롤러까지 전파된 저장소 예외를 공통 처리한 경우 1회 증가. */
    public void recordApiBackendFailure() {
        apiBackendFailures.increment();
    }

    /** Redis 장애 후 Kafka로 대기열 진입 의도를 성공적으로 발행한 경우 1회 증가. */
    public void recordKafkaJoinFallbackPublished() {
        kafkaJoinFallbackPublished.increment();
    }

    /** Kafka 발행 단계에서 예외가 난 경우 1회 증가. */
    public void recordKafkaJoinFallbackPublishFailed() {
        kafkaJoinFallbackPublishFailed.increment();
    }

    /** 컨슈머가 Redis에 {@code joinQueueFromRecovery}까지 반영한 경우 1회 증가. */
    public void recordKafkaJoinFallbackRecovered() {
        kafkaJoinFallbackRecovered.increment();
    }

    /** 재시도 소진 후 DLT 핸들러로 넘어온 경우 1회 증가. */
    public void recordKafkaJoinFallbackDlt() {
        kafkaJoinFallbackDlt.increment();
    }
}
