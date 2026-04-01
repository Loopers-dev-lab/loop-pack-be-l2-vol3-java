package com.loopers.application.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code queue.position.*} — 순번 조회 시 예상 대기 시간 산정용 처리량(TPS). */
@ConfigurationProperties(prefix = "queue.position")
public record QueuePositionProperties(double throughputTps) {
}
