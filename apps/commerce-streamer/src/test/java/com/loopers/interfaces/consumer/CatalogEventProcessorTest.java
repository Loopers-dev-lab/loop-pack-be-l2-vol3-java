package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.domain.ranking.RankingService;
import com.loopers.domain.ranking.RankingWeight;
import com.loopers.infrastructure.monitoring.ConsumerMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("CatalogEventProcessor 단위 테스트")
class CatalogEventProcessorTest {

    @Mock
    ProductMetricsService productMetricsService;

    @Mock
    RankingService rankingService;

    @Mock
    ConsumerMetrics consumerMetrics;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    CatalogEventProcessor catalogEventProcessor;

    private ConsumerRecord<Object, Object> record(Map<String, Object> value) {
        return new ConsumerRecord<>("catalog-events", 0, 0L, "key", value);
    }

    @Test
    @DisplayName("PRODUCT_VIEWED 배치는 productId별로 합산되어 DB/Redis 배치 호출되고, 성공 메트릭은 총 이벤트 수만큼 기록된다")
    void processBatch_ProductViewedBatch_ShouldAggregateAndRecordProcessed() {
        List<ConsumerRecord<Object, Object>> records = List.of(
                record(Map.of("eventType", "PRODUCT_VIEWED", "productId", 100L)),
                record(Map.of("eventType", "PRODUCT_VIEWED", "productId", 100L)),
                record(Map.of("eventType", "PRODUCT_VIEWED", "productId", 200L))
        );

        catalogEventProcessor.processBatch(records);

        verify(productMetricsService).incrementViewCountBy(100L, 2);
        verify(productMetricsService).incrementViewCountBy(200L, 1);

        Map<Long, Double> expectedScores = new HashMap<>();
        expectedScores.put(100L, RankingWeight.VIEW * 2);
        expectedScores.put(200L, RankingWeight.VIEW);
        verify(rankingService).incrementScoreBatch(expectedScores);

        verify(consumerMetrics).recordCatalogProcessed(3L);
        verify(consumerMetrics, never()).recordCatalogFailed(anyLong());
    }

    @Test
    @DisplayName("PRODUCT_VIEWED 배치에서 Redis 실패 시 DB는 이미 반영됐지만 실패 메트릭이 총 이벤트 수만큼 기록된다")
    void processBatch_ProductViewedRedisFailure_ShouldRecordFailureWithTotalEventCount() {
        List<ConsumerRecord<Object, Object>> records = List.of(
                record(Map.of("eventType", "PRODUCT_VIEWED", "productId", 100L)),
                record(Map.of("eventType", "PRODUCT_VIEWED", "productId", 100L)),
                record(Map.of("eventType", "PRODUCT_VIEWED", "productId", 200L))
        );
        doThrow(new RuntimeException("redis down"))
                .when(rankingService).incrementScoreBatch(any());

        catalogEventProcessor.processBatch(records);

        verify(productMetricsService).incrementViewCountBy(100L, 2);
        verify(productMetricsService).incrementViewCountBy(200L, 1);
        verify(consumerMetrics).recordCatalogFailed(3L);
        verify(consumerMetrics, never()).recordCatalogProcessed(anyLong());
    }

    @Test
    @DisplayName("PRODUCT_LIKED 이벤트는 DB 증가 + 랭킹 멱등 증가 호출 후 성공 메트릭이 기록된다")
    void processBatch_ProductLiked_ShouldIncrementLikeAndRecordProcessed() {
        List<ConsumerRecord<Object, Object>> records = List.of(
                record(Map.of(
                        "eventType", "PRODUCT_LIKED",
                        "productId", 100L,
                        "userId", 1L,
                        "occurredAt", "2026-04-10T14:00:00"
                ))
        );

        catalogEventProcessor.processBatch(records);

        verify(productMetricsService).incrementLikeCount(100L);
        verify(rankingService).incrementLikeScoreIfAbsent(
                eq(100L), eq(1L), eq(RankingWeight.LIKE), any());
        verify(consumerMetrics).recordCatalogProcessed();
        verify(consumerMetrics, never()).recordCatalogFailed();
    }

    @Test
    @DisplayName("PRODUCT_UNLIKED 이벤트는 DB 감소 + 랭킹 멱등 감소 호출 후 성공 메트릭이 기록된다")
    void processBatch_ProductUnliked_ShouldDecrementLikeAndRecordProcessed() {
        List<ConsumerRecord<Object, Object>> records = List.of(
                record(Map.of(
                        "eventType", "PRODUCT_UNLIKED",
                        "productId", 300L,
                        "userId", 2L,
                        "occurredAt", "2026-04-10T14:00:00"
                ))
        );

        catalogEventProcessor.processBatch(records);

        verify(productMetricsService).decrementLikeCount(300L);
        verify(rankingService).decrementLikeScoreIfPresent(
                eq(300L), eq(2L), eq(RankingWeight.LIKE), any());
        verify(consumerMetrics).recordCatalogProcessed();
    }

    @Test
    @DisplayName("PRODUCT_LIKED 처리 중 예외가 발생하면 실패 메트릭이 기록되고 배치의 나머지는 계속 처리된다")
    void processBatch_ProductLikedFailure_ShouldRecordFailureAndContinue() {
        List<ConsumerRecord<Object, Object>> records = List.of(
                record(Map.of(
                        "eventType", "PRODUCT_LIKED",
                        "productId", 100L,
                        "userId", 1L,
                        "occurredAt", "2026-04-10T14:00:00"
                )),
                record(Map.of(
                        "eventType", "PRODUCT_LIKED",
                        "productId", 200L,
                        "userId", 2L,
                        "occurredAt", "2026-04-10T14:00:00"
                ))
        );
        doThrow(new RuntimeException("db down"))
                .when(productMetricsService).incrementLikeCount(100L);

        catalogEventProcessor.processBatch(records);

        verify(productMetricsService).incrementLikeCount(100L);
        verify(productMetricsService).incrementLikeCount(200L);
        verify(rankingService).incrementLikeScoreIfAbsent(
                eq(200L), eq(2L), anyDouble(), any());
        verify(consumerMetrics).recordCatalogFailed();
        verify(consumerMetrics).recordCatalogProcessed();
    }

    @Test
    @DisplayName("알 수 없는 eventType은 어떤 서비스도 호출하지 않는다")
    void processBatch_UnknownEventType_ShouldNotCallServices() {
        List<ConsumerRecord<Object, Object>> records = List.of(
                record(Map.of("eventType", "UNKNOWN_EVENT", "productId", 100L))
        );

        catalogEventProcessor.processBatch(records);

        verifyNoInteractions(productMetricsService, rankingService, consumerMetrics);
    }

    @Test
    @DisplayName("메시지가 Map/String 이 아니면 무시한다")
    void processBatch_NonMapValue_ShouldSkip() {
        ConsumerRecord<Object, Object> invalid =
                new ConsumerRecord<>("catalog-events", 0, 0L, "key", 12345);

        catalogEventProcessor.processBatch(List.of(invalid));

        verifyNoInteractions(productMetricsService, rankingService, consumerMetrics);
    }
}
