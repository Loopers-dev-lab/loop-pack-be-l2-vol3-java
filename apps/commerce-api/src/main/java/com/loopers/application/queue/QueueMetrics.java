package com.loopers.application.queue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class QueueMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<String, Counter> enterCounterMap = new ConcurrentHashMap<>();
    private final Map<String, Counter> tokenIssuedCounterMap = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> waitingSizeMap = new ConcurrentHashMap<>();

    public QueueMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordEnter(String eventId) {
        enterCounterMap.computeIfAbsent(eventId, id ->
                Counter.builder("queue.enter.total")
                        .tag("event_id", id)
                        .register(meterRegistry)
        ).increment();
    }

    public void recordTokenIssued(String eventId, int count) {
        tokenIssuedCounterMap.computeIfAbsent(eventId, id ->
                Counter.builder("queue.token.issued.total")
                        .tag("event_id", id)
                        .register(meterRegistry)
        ).increment(count);
    }

    public void updateWaitingSize(String eventId, long size) {
        AtomicLong gauge = waitingSizeMap.computeIfAbsent(eventId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder("queue.waiting.size", value, AtomicLong::doubleValue)
                    .tag("event_id", id)
                    .register(meterRegistry);
            return value;
        });
        gauge.set(size);
    }
}
