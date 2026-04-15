package com.loopers.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

/**
 * DLQ(DeadLetterPublishingRecoverer)로 전송된 메시지 수. 로드맵 3단계 관측용.
 */
@Component
public class KafkaCollectorDlqMetrics {

    private final MeterRegistry meterRegistry;

    public KafkaCollectorDlqMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordDlqSend(String sourceTopic) {
        meterRegistry
                .counter("kafka.collector.events.dlq", Tags.of("topic", sourceTopic == null ? "unknown" : sourceTopic))
                .increment();
    }
}
