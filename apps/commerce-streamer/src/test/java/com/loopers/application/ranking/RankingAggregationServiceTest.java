package com.loopers.application.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.ranking.ProductDailyAggregate;
import com.loopers.domain.ranking.ProductMetricsHourlyRepository;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.domain.ranking.RankingWeights;
import com.loopers.domain.ranking.RankingWriter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingAggregationService 단위 테스트")
class RankingAggregationServiceTest {

    private BatchAggregator batchAggregator;
    private EventHandledRepository eventHandledRepository;
    private ProductMetricsHourlyRepository metricsRepository;
    private RankingScoreCalculator calculator;
    private RankingWriter rankingWriter;
    private RankingAggregationService service;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime FIXED =
            LocalDateTime.of(2026, 4, 9, 14, 37, 22);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED.atZone(KST).toInstant(), KST);

    @BeforeEach
    void setUp() {
        batchAggregator = new BatchAggregator(new ObjectMapper());
        eventHandledRepository = mock(EventHandledRepository.class);
        metricsRepository = mock(ProductMetricsHourlyRepository.class);
        calculator = new RankingScoreCalculator(new RankingWeights(0.1, 0.2, 0.7));
        rankingWriter = mock(RankingWriter.class);

        // 단위 테스트용 no-op TransactionTemplate — execute 및 executeWithoutResult 즉시 콜백 실행
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new RankingAggregationService(
                batchAggregator,
                eventHandledRepository,
                metricsRepository,
                calculator,
                rankingWriter,
                FIXED_CLOCK,
                transactionTemplate
        );
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("catalog-events", 0, 0L, "key", value);
    }

    @Nested
    @DisplayName("processCatalogBatch")
    class CatalogBatch {

        @Test
        @DisplayName("정상 경로 — 상품별 UPSERT + snapshot + ZADD + event_handled 저장")
        void happyPath() {
            // given
            when(eventHandledRepository.findExistingEventIds(any())).thenReturn(Set.of());
            when(metricsRepository.snapshotsByDate(any(), any()))
                    .thenReturn(Map.of(100L, new ProductDailyAggregate(100L, 3L, 1L, 0L, BigDecimal.ZERO)));
            List<ConsumerRecord<String, String>> records = List.of(
                    record("""
                            {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":100}}
                            """),
                    record("""
                            {"eventId":"v2","eventType":"PRODUCT_VIEWED","data":{"productId":100}}
                            """),
                    record("""
                            {"eventId":"v3","eventType":"PRODUCT_VIEWED","data":{"productId":100}}
                            """)
            );
            // when
            service.processCatalogBatch(records);
            // then
            LocalDateTime expectedBucket = LocalDateTime.of(2026, 4, 9, 14, 0, 0);
            verify(metricsRepository, times(1))
                    .upsertIncrements(eq(100L), eq(expectedBucket), eq(3L), eq(0L), eq(0L), eq(BigDecimal.ZERO));
            verify(metricsRepository, times(1)).snapshotsByDate(eq(Set.of(100L)), eq(LocalDate.of(2026, 4, 9)));
            verify(rankingWriter, times(1)).upsertScores(eq("ranking:all:20260409"),
                    argThat((Map<Long, Double> m) -> m.containsKey(100L)));
            verify(eventHandledRepository, times(1))
                    .saveAllNew(argThat((Set<String> ids) -> ids.containsAll(Set.of("v1", "v2", "v3"))));
        }

        @Test
        @DisplayName("이미 처리된 eventId 는 필터링되어 중복 반영되지 않는다")
        void idempotencyFilter() {
            // given
            when(eventHandledRepository.findExistingEventIds(any())).thenReturn(Set.of("v1"));
            when(metricsRepository.snapshotsByDate(any(), any()))
                    .thenReturn(Map.of(100L, new ProductDailyAggregate(100L, 1L, 0L, 0L, BigDecimal.ZERO)));
            List<ConsumerRecord<String, String>> records = List.of(
                    record("""
                            {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":100}}
                            """),
                    record("""
                            {"eventId":"v2","eventType":"PRODUCT_VIEWED","data":{"productId":100}}
                            """)
            );
            // when
            service.processCatalogBatch(records);
            // then — v2 만 반영되어 view=1
            LocalDateTime expectedBucket = LocalDateTime.of(2026, 4, 9, 14, 0, 0);
            verify(metricsRepository, times(1))
                    .upsertIncrements(eq(100L), eq(expectedBucket), eq(1L), eq(0L), eq(0L), eq(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("전부 이미 처리된 배치라도 ZADD 동기화는 수행하여 Redis를 복구한다")
        void allAlreadyHandled() {
            // given
            when(eventHandledRepository.findExistingEventIds(any())).thenReturn(Set.of("v1", "v2"));
            when(metricsRepository.snapshotsByDate(any(), any()))
                    .thenReturn(Map.of(
                            1L, new ProductDailyAggregate(1L, 1L, 0L, 0L, BigDecimal.ZERO),
                            2L, new ProductDailyAggregate(2L, 1L, 0L, 0L, BigDecimal.ZERO)
                    ));

            List<ConsumerRecord<String, String>> records = List.of(
                    record("""
                            {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """),
                    record("""
                            {"eventId":"v2","eventType":"PRODUCT_VIEWED","data":{"productId":2}}
                            """)
            );
            // when
            service.processCatalogBatch(records);
            // then
            verify(metricsRepository, never()).upsertIncrements(any(), any(), anyLong(), anyLong(), anyLong(), any());
            verify(eventHandledRepository, never()).saveAllNew(any());

            // 핵심: DB는 패스하지만 Redis ZADD 는 재계산해서 호출되어야 함 (부분 실패 후 Retry 시 자가 복구)
            verify(metricsRepository, times(1)).snapshotsByDate(
                    argThat(ids -> ids.containsAll(Set.of(1L, 2L))), any());
            verify(rankingWriter, times(1)).upsertScores(eq("ranking:all:20260409"),
                    argThat((Map<Long, Double> m) -> m.containsKey(1L) && m.containsKey(2L)));
        }

        @Test
        @DisplayName("bucket_hour 는 KST 현재 시각을 시간 단위로 절삭한다")
        void bucketTruncation() {
            // given
            when(eventHandledRepository.findExistingEventIds(any())).thenReturn(Set.of());
            when(metricsRepository.snapshotsByDate(any(), any()))
                    .thenReturn(Map.of(1L, new ProductDailyAggregate(1L, 1L, 0L, 0L, BigDecimal.ZERO)));
            AtomicReference<LocalDateTime> captured = new AtomicReference<>();
            org.mockito.Mockito.doAnswer(inv -> {
                captured.set(inv.getArgument(1));
                return null;
            }).when(metricsRepository).upsertIncrements(any(), any(), anyLong(), anyLong(), anyLong(), any());

            // when
            service.processCatalogBatch(List.of(record("""
                    {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                    """)));

            // then
            assertThat(captured.get()).isEqualTo(LocalDateTime.of(2026, 4, 9, 14, 0, 0));
        }
    }

    @Nested
    @DisplayName("processOrderBatch")
    class OrderBatch {

        @Test
        @DisplayName("order_count/order_amount 가 라인 합산으로 반영된다")
        void orderLinesAggregated() {
            // given
            when(eventHandledRepository.findExistingEventIds(any())).thenReturn(Set.of());
            when(metricsRepository.snapshotsByDate(any(), any()))
                    .thenReturn(Map.of(
                            1L, new ProductDailyAggregate(1L, 0L, 0L, 2L, BigDecimal.valueOf(20000)),
                            2L, new ProductDailyAggregate(2L, 0L, 0L, 1L, BigDecimal.valueOf(50000))
                    ));

            List<ConsumerRecord<String, String>> records = List.of(new ConsumerRecord<>(
                    "order-events", 0, 0L, "key", """
                    {"eventId":"o1","eventType":"ORDER_PAID","data":{"orderedProducts":[
                      {"productId":1,"quantity":2,"unitPrice":10000},
                      {"productId":2,"quantity":1,"unitPrice":50000}
                    ]}}
                    """));
            // when
            service.processOrderBatch(records);
            // then
            verify(metricsRepository).upsertIncrements(
                    eq(1L), any(), eq(0L), eq(0L), eq(2L), eq(BigDecimal.valueOf(20000)));
            verify(metricsRepository).upsertIncrements(
                    eq(2L), any(), eq(0L), eq(0L), eq(1L), eq(BigDecimal.valueOf(50000)));
            verify(rankingWriter, times(1)).upsertScores(eq("ranking:all:20260409"),
                    argThat((Map<Long, Double> m) -> m.size() == 2));
        }
    }

    @Nested
    @DisplayName("N+1 방지 — bulk 호출 검증")
    class BulkCallProtection {

        @Test
        @DisplayName("상품 50개 배치 처리 시 snapshotsByDate 1회 + upsertScores 1회만 호출한다")
        void nProductsBatch_singleDbAndRedisCall() {
            // given
            int n = 50;
            List<ConsumerRecord<String, String>> records = new ArrayList<>(n);
            Map<Long, ProductDailyAggregate> snapshots = new LinkedHashMap<>();
            for (long i = 1; i <= n; i++) {
                records.add(record("""
                        {"eventId":"v%d","eventType":"PRODUCT_VIEWED","data":{"productId":%d}}
                        """.formatted(i, i)));
                snapshots.put(i, new ProductDailyAggregate(i, 1L, 0L, 0L, BigDecimal.ZERO));
            }
            when(eventHandledRepository.findExistingEventIds(any())).thenReturn(Set.of());
            when(metricsRepository.snapshotsByDate(any(), any())).thenReturn(snapshots);

            // when
            service.processCatalogBatch(records);

            // then — DB 1회, Redis 1회 (N 회 아님)
            verify(metricsRepository, times(1)).snapshotsByDate(any(), any());
            verify(rankingWriter, times(1)).upsertScores(eq("ranking:all:20260409"),
                    argThat((Map<Long, Double> m) -> m.size() == n));
            verify(rankingWriter, never()).upsertScore(any(), any(), anyDouble());
        }
    }
}
