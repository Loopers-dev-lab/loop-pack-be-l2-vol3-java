package com.loopers.collector.config;

import com.loopers.collector.cleanup.EventHandledCleanupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 기동 시 collector 멱등·보관 설정을 한 번 로깅해 운영/장애 시 기대값과 맞는지 확인하기 쉽게 한다.
 */
@Component
public class CollectorStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(CollectorStartupLogger.class);

    private final CollectorIdempotencyProperties idempotencyProperties;
    private final EventHandledCleanupProperties cleanupProperties;

    public CollectorStartupLogger(
            CollectorIdempotencyProperties idempotencyProperties,
            EventHandledCleanupProperties cleanupProperties) {
        this.idempotencyProperties = idempotencyProperties;
        this.cleanupProperties = cleanupProperties;
    }

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void logCollectorSettings() {
        log.info(
                "[collector] 경량 이벤트 Redis 멱등 TTL={}일 | event_handled 보관 retention={}일 (기술 로그·감사 아님) | "
                        + "정리 enabled={} batchSize={} maxLoopsPerRun={} maxRowsPerRun={} fixedDelayMs={} | "
                        + "Kafka 브로커 보존·consumer 최대 중단 허용 시간·Redis TTL을 정책으로 맞출 것 (예: retention ≤ 브로커 보존).",
                idempotencyProperties.redisTtlDays(),
                cleanupProperties.retentionDays(),
                cleanupProperties.enabled(),
                cleanupProperties.batchSize(),
                cleanupProperties.maxLoopsPerRun(),
                cleanupProperties.maxRowsPerRun(),
                cleanupProperties.fixedDelayMs()
        );
    }
}
