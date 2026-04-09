package com.loopers.application.ranking;

import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.ranking.ProductDailyAggregate;
import com.loopers.domain.ranking.ProductMetricsHourlyRepository;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.domain.ranking.RankingWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * R9 랭킹 파이프라인의 핵심 서비스.
 *
 * <p>Kafka 배치 리스너가 받은 {@code List<ConsumerRecord>} 를 받아서:
 * <ol>
 *   <li>모든 eventId 를 추출해 {@code event_handled} 기반으로 이미 처리된 것 필터링 (멱등)</li>
 *   <li>신규 레코드만 상품별 {@link MetricDelta} 로 압축 (배치 aggregate)</li>
 *   <li>[TX] 상품별 Native UPSERT → snapshot 재계산 → {@code event_handled} INSERT IGNORE</li>
 *   <li>[TX 커밋 후] 상품별 ZADD upsert</li>
 * </ol>
 *
 * <p>트랜잭션 경계 (R9 리뷰 #3 반영):
 * <ul>
 *   <li>DB I/O (UPSERT + snapshot SELECT + event_handled INSERT) 는 한 트랜잭션에 묶임</li>
 *   <li>Redis ZADD 는 <b>TX 커밋 이후</b> 호출되어, DB 커넥션을 외부 I/O 시간만큼 점유하지 않음</li>
 *   <li>ZADD 실패 시 컨슈머 ack 미전송 → Kafka 재전달 → 다음 배치에서 snapshot 재계산 → ZADD 자연 복구</li>
 *   <li>DB 가 항상 원장이며, Redis 는 DB 보다 앞서가는 일관성 윈도가 존재하지 않음</li>
 * </ul>
 *
 * <p>bucket_hour 계산은 `Asia/Seoul` 시간대의 "현재 시간" 을 시간 단위로 절삭한 값을 사용한다.
 * 테스트에서는 주입된 {@link Clock} 을 바꾸어 고정할 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingAggregationService {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BatchAggregator batchAggregator;
    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final RankingScoreCalculator rankingScoreCalculator;
    private final RankingWriter rankingWriter;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    /**
     * catalog-events 배치를 처리한다.
     */
    public void processCatalogBatch(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) return;

        Set<Long> allProductIdsInBatch = batchAggregator.extractProductIds(records);

        List<ConsumerRecord<String, String>> fresh = filterAlreadyHandled(records);
        if (!fresh.isEmpty()) {
            Map<Long, MetricDelta> perProduct = batchAggregator.aggregateCatalog(fresh);
            persistDeltas(perProduct);
        }

        Map<Long, Double> scoresToPublish = recalculateFromSnapshot(allProductIdsInBatch);
        publishScores(scoresToPublish);
    }

    /**
     * order-events 배치를 처리한다.
     */
    public void processOrderBatch(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) return;

        Set<Long> allProductIdsInBatch = batchAggregator.extractProductIds(records);

        List<ConsumerRecord<String, String>> fresh = filterAlreadyHandled(records);
        if (!fresh.isEmpty()) {
            Map<Long, MetricDelta> perProduct = batchAggregator.aggregateOrder(fresh);
            persistDeltas(perProduct);
        }

        Map<Long, Double> scoresToPublish = recalculateFromSnapshot(allProductIdsInBatch);
        publishScores(scoresToPublish);
    }

    // 이미 처리된 eventId 를 필터링하여 신규 레코드만 반환한다.
    // existing 이 비면 전체 records 를 그대로 반환하여 DB IN 쿼리를 생략한다.
    private List<ConsumerRecord<String, String>> filterAlreadyHandled(List<ConsumerRecord<String, String>> records) {
        if (records == null || records.isEmpty()) return Collections.emptyList();

        List<String> eventIds = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            String eid = batchAggregator.extractEventId(record);
            if (eid != null) eventIds.add(eid);
        }
        if (eventIds.isEmpty()) return Collections.emptyList();

        Set<String> existing = eventHandledRepository.findExistingEventIds(eventIds);
        if (existing.isEmpty()) return records;

        List<ConsumerRecord<String, String>> fresh = new ArrayList<>(records.size());
        for (ConsumerRecord<String, String> record : records) {
            String eid = batchAggregator.extractEventId(record);
            if (eid == null || !existing.contains(eid)) {
                fresh.add(record);
            } else {
                log.debug("skip already-handled eventId={}", eid);
            }
        }
        return fresh;
    }

    /**
     * DB 영속화. Redis I/O 는 호출자(TX 밖) 에서 별도로 수행한다.
     */
    private void persistDeltas(Map<Long, MetricDelta> perProduct) {
        if (perProduct.isEmpty()) return;
        transactionTemplate.executeWithoutResult(status -> doPersistDeltas(perProduct));
    }

    private void doPersistDeltas(Map<Long, MetricDelta> perProduct) {
        LocalDateTime bucket = LocalDateTime.now(clock.withZone(KST)).truncatedTo(ChronoUnit.HOURS);
        Set<String> allEventIds = new HashSet<>();

        for (Map.Entry<Long, MetricDelta> entry : perProduct.entrySet()) {
            Long productId = entry.getKey();
            MetricDelta delta = entry.getValue();
            allEventIds.addAll(delta.eventIds());
            if (delta.isEmpty()) continue;

            productMetricsHourlyRepository.upsertIncrements(
                    productId,
                    bucket,
                    delta.view(),
                    delta.like(),
                    delta.order(),
                    delta.amount()
            );
        }

        if (!allEventIds.isEmpty()) {
            eventHandledRepository.saveAllNew(allEventIds);
        }
    }

    /**
     * 주어진 상품들의 최신 스냅샷을 DB 에서 조회하여 점수를 다시 계산한다.
     * (Redis 재시도 시 점수 정상화를 위한 연산)
     */
    private Map<Long, Double> recalculateFromSnapshot(Set<Long> productIds) {
        if (productIds.isEmpty()) return Collections.emptyMap();
        
        LocalDate today = LocalDate.now(clock.withZone(KST));
        Map<Long, Double> scores = new LinkedHashMap<>();
        
        for (Long productId : productIds) {
            ProductDailyAggregate snapshot =
                    productMetricsHourlyRepository.snapshotByDate(productId, today);
            scores.put(productId, rankingScoreCalculator.calculate(snapshot));
        }
        return scores;
    }

    /**
     * TX 커밋 이후 호출 — 상품별 ZADD upsert. 실패 시 예외 전파 → consumer ack 미전송 → 재배달.
     */
    private void publishScores(Map<Long, Double> scores) {
        if (scores.isEmpty()) return;
        String todayKey = RankingKey.daily(LocalDate.now(clock.withZone(KST)));
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            rankingWriter.upsertScore(todayKey, entry.getKey(), entry.getValue());
        }
    }

}
