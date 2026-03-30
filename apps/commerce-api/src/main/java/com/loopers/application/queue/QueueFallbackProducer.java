package com.loopers.application.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueFallbackProducer {

    private static final String TOPIC = "queue-fallback-orders";

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final AtomicLong fallbackCount = new AtomicLong(0);

    public void publishFallbackOrder(Long memberId) {
        long count = fallbackCount.incrementAndGet();
        kafkaTemplate.send(TOPIC, String.valueOf(memberId), Map.of(
                "memberId", memberId,
                "timestamp", System.currentTimeMillis(),
                "fallbackCount", count
        ));
        log.warn("[KAFKA_FALLBACK] Order fallback published. memberId={}, total={}", memberId, count);
    }

    public long getFallbackCount() {
        return fallbackCount.get();
    }
}
