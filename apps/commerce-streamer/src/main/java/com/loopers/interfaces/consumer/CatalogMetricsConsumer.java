package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.CatalogMetricsProcessor;
import com.loopers.infrastructure.dlq.DlqPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 카탈로그 메트릭 Consumer — 메시지 수신 + ACK + DLQ
 *
 * Interfaces 레이어의 책임: "요청 수신"
 *   Controller가 HTTP 요청을 받아서 Facade에 위임하듯이,
 *   Consumer가 Kafka 메시지를 받아서 Processor에 위임한다.
 *
 * 비즈니스 처리는 CatalogMetricsProcessor(@Service)에 위임:
 *   → 프록시를 통한 호출 → @Transactional 정상 동작
 *   → self-invocation 방지 → increment + event_handled 같은 TX 보장
 *
 * 랭킹 책임 분리 (케브 피드백):
 *   랭킹 delta 수집/flush는 별도 RankingConsumer(ranking-group)로 분리.
 *   이 Consumer는 메트릭 집계(product_metrics, products.like_count)에만 집중한다.
 *   → Redis 장애 시 metrics 파이프라인에 영향 없음
 *   → 도메인 책임 분리 (아키텍처 퀀텀)
 */
@Component
public class CatalogMetricsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogMetricsConsumer.class);

    private final CatalogMetricsProcessor processor;
    private final DlqPublisher dlqPublisher;

    public CatalogMetricsConsumer(CatalogMetricsProcessor processor,
                                   DlqPublisher dlqPublisher) {
        this.processor = processor;
        this.dlqPublisher = dlqPublisher;
    }

    @KafkaListener(
            topics = "catalog-events-v1",
            groupId = "catalog-metrics-group",
            containerFactory = "BATCH_LISTENER_DEFAULT"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                String eventType = getHeader(record, "X-Event-Type");
                String outboxId = getHeader(record, "X-Outbox-Id");
                String payload = record.value().toString();

                if (eventType == null) {
                    log.warn("[CatalogMetrics] X-Event-Type 헤더 없음 — partition={}, offset={}",
                            record.partition(), record.offset());
                    continue;
                }

                processor.process(eventType, outboxId, payload);

            } catch (Exception e) {
                log.error("[CatalogMetrics] 처리 실패 → DLQ — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
                dlqPublisher.sendToDlq(record, e);
            }
        }

        ack.acknowledge();
    }

    private String getHeader(ConsumerRecord<Object, Object> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
