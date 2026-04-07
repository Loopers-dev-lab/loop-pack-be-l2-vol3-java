package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code queue.order.*} — 주문 API 입장 토큰 관문 설정. */
@ConfigurationProperties(prefix = "queue.order")
public record QueueOrderProperties(boolean requireEntryToken) {
}
