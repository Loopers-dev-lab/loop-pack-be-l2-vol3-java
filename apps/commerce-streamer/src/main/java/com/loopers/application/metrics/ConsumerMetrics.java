package com.loopers.application.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ConsumerMetrics {

    private final MeterRegistry meterRegistry;

    public ConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordProcessed(String topic, String groupId, String eventType, long durationMs) {
        Counter.builder("consumer.event.processed")
                .tag("topic", topic)
                .tag("group_id", groupId)
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();

        Timer.builder("consumer.event.duration")
                .tag("topic", topic)
                .tag("group_id", groupId)
                .tag("event_type", eventType)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    public void recordSkipped(String topic, String groupId, String eventType) {
        Counter.builder("consumer.event.skipped")
                .tag("topic", topic)
                .tag("group_id", groupId)
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }

    public void recordFailed(String topic, String groupId, String eventType) {
        Counter.builder("consumer.event.failed")
                .tag("topic", topic)
                .tag("group_id", groupId)
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }
}
