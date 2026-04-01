package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code queue.fallback.*} — Redis 장애 시 Kafka 비동기 접수 사용 여부. */
@ConfigurationProperties(prefix = "queue.fallback")
public record QueueFallbackProperties(boolean enabled) {
}
