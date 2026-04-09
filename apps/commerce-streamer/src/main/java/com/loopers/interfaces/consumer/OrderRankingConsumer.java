package com.loopers.interfaces.consumer;

import com.loopers.application.ranking.RankingAggregationService;
import com.loopers.config.MetricsKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R9 랭킹 파이프라인 — order-events (주문 결제 완료) 배치 소비자.
 *
 * <p>R7 단건 처리 컨슈머를 대체한다 — 같은 토픽/같은 groupId 로 이어서 소비한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderRankingConsumer {

    private final RankingAggregationService rankingAggregationService;

    @KafkaListener(
            topics = "order-events",
            groupId = "metrics-group",
            containerFactory = MetricsKafkaConfig.RANKING_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> records, Acknowledgment ack) {
        if (records == null || records.isEmpty()) {
            ack.acknowledge();
            return;
        }
        try {
            rankingAggregationService.processOrderBatch(records);
            ack.acknowledge();
            log.info("order-events 배치 처리 완료: size={}", records.size());
        } catch (Exception e) {
            log.error("order-events 배치 처리 실패 — ACK 미전송. size={}", records.size(), e);
            throw e;
        }
    }
}
