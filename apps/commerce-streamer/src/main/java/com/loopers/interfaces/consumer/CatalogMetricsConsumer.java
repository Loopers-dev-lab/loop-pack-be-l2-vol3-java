package com.loopers.interfaces.consumer;

import com.loopers.application.metrics.CatalogMetricsProcessor;
import com.loopers.application.metrics.CatalogMetricsProcessor.ProcessResult;
import com.loopers.application.metrics.CatalogMetricsProcessor.RankingDelta;
import com.loopers.application.ranking.RankingScoreUpdater;
import com.loopers.infrastructure.dlq.DlqPublisher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 카탈로그 메트릭 Consumer — 메시지 수신 + ACK + DLQ + 랭킹 배치 flush
 *
 * Interfaces 레이어의 책임: "요청 수신"
 *   Controller가 HTTP 요청을 받아서 Facade에 위임하듯이,
 *   Consumer가 Kafka 메시지를 받아서 Processor에 위임한다.
 *
 * 비즈니스 처리는 CatalogMetricsProcessor(@Service)에 위임:
 *   → 프록시를 통한 호출 → @Transactional 정상 동작
 *   → self-invocation 방지 → increment + event_handled 같은 TX 보장
 *
 * 랭킹 배치 정제 (R9):
 *   Processor가 반환한 RankingDelta를 배치 내에서 합산한 뒤,
 *   배치 루프 완료 후 RankingScoreUpdater.flushBatch()로 일괄 적재.
 *   배치 3000건 중 유니크 상품 N개 → ZINCRBY N회 (Pipeline)로 RTT 절감.
 *
 *   at-most-once 보장 유지:
 *   - process() 성공(DB TX 커밋) → delta 수집 ✓
 *   - process() 실패/스킵 → delta 미수집 ✓
 *   - flushBatch() 실패 → best-effort 누락 (랭킹 근사치 도메인에 적합)
 */
@Component
public class CatalogMetricsConsumer {

    private static final Logger log = LoggerFactory.getLogger(CatalogMetricsConsumer.class);

    private final CatalogMetricsProcessor processor;
    private final RankingScoreUpdater rankingScoreUpdater;
    private final DlqPublisher dlqPublisher;

    public CatalogMetricsConsumer(CatalogMetricsProcessor processor,
                                   RankingScoreUpdater rankingScoreUpdater,
                                   DlqPublisher dlqPublisher) {
        this.processor = processor;
        this.rankingScoreUpdater = rankingScoreUpdater;
        this.dlqPublisher = dlqPublisher;
    }

    @KafkaListener(
            topics = "catalog-events-v1",
            groupId = "catalog-metrics-group",
            containerFactory = "BATCH_LISTENER_DEFAULT"
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment ack) {
        Map<Long, Double> batchScores = new HashMap<>();

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

                ProcessResult result = processor.process(eventType, outboxId, payload);

                // DB TX 커밋 완료된 건만 랭킹 delta 수집
                if (result.processed()) {
                    for (RankingDelta delta : result.deltas()) {
                        batchScores.merge(delta.productId(), delta.delta(), Double::sum);
                    }
                }

            } catch (Exception e) {
                log.error("[CatalogMetrics] 처리 실패 → DLQ — partition={}, offset={}, error={}",
                        record.partition(), record.offset(), e.getMessage(), e);
                dlqPublisher.sendToDlq(record, e);
            }
        }

        // 배치 합산된 점수를 일간 + 시간 키에 일괄 적재
        if (!batchScores.isEmpty()) {
            rankingScoreUpdater.flushBatch(batchScores);
        }

        ack.acknowledge();
    }

    private String getHeader(ConsumerRecord<Object, Object> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
