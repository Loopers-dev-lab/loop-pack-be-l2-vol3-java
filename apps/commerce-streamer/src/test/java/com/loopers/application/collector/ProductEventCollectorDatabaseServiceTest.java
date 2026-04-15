package com.loopers.application.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingMetricsRedisSyncService;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.domain.ranking.RankingScoreWeights;
import com.loopers.domain.ranking.RankingWriteRepository;
import com.loopers.infrastructure.collector.EventHandledJpaRepository;
import com.loopers.infrastructure.collector.ProductMetricsModel;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductEventCollectorDatabaseServiceTest {

    @Mock
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Mock
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Mock
    private RankingWriteRepository rankingWriteRepository;

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Mock
    private ProductViewContributionLimiter productViewContributionLimiter;

    @Mock
    private ProductSoldContributionLimiter productSoldContributionLimiter;

    private SimpleMeterRegistry meterRegistry;
    private ProductEventCollectorDatabaseService databaseService;

    /**
     * 테스트 설정
     * @param productMetricsJpaRepository 상품 매트릭 JPA 리포지토리
     * @param rankingWriteRepository 랭킹 쓰기 리포지토리
     */
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        RankingMetricsRedisSyncService rankingSync = new RankingMetricsRedisSyncService(
                new RankingScoreCalculator(RankingScoreWeights.questExample()),
                rankingWriteRepository
        );
        databaseService = new ProductEventCollectorDatabaseService(
                eventHandledJpaRepository,
                productMetricsJpaRepository,
                rankingSync,
                kafkaTemplate,
                productViewContributionLimiter,
                productSoldContributionLimiter,
                meterRegistry,
                ".DLQ"
        );
    }

    @Test
    @DisplayName("신규 PRODUCT_LIKE_CHANGED 이벤트는 event_handled 저장 후 like delta를 반영한다.")
    void processDb_whenNewLikeEvent_shouldRecordHandledAndUpdateMetrics() {
        ProductMetricsModel metrics = org.mockito.Mockito.mock(ProductMetricsModel.class);
        when(metrics.getProductId()).thenReturn(101L);
        when(metrics.getViewCount()).thenReturn(0L);
        when(metrics.getLikeCount()).thenReturn(1L);
        when(metrics.getSoldQuantity()).thenReturn(0L);
        when(productMetricsJpaRepository.findById(101L)).thenReturn(Optional.of(metrics));

        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                0,
                5L,
                "101",
                envelopeJson("evt-1", "PRODUCT_LIKE_CHANGED", "2026-03-26T00:00:00Z", 101L, "LIKED").getBytes()
        );

        databaseService.processDb(record, parse(record.value()));

        verify(eventHandledJpaRepository).saveAndFlush(any());
        verify(productMetricsJpaRepository).applyLikeDeltaIfNewer(
                eq(101L),
                eq(1L),
                eq(Instant.parse("2026-03-26T00:00:00Z"))
        );
        verify(rankingWriteRepository).upsertScore(
                eq("ranking:all:20260326"),
                eq("101"),
                eq(0.2d),
                eq(Duration.ofDays(2))
        );
    }

    @Test
    @DisplayName("이미 처리된 event_id(PK 충돌)면 metrics 갱신 없이 스킵한다.")
    void processDb_whenDuplicateEvent_shouldSkipMetricsUpdate() {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                1,
                10L,
                "101",
                envelopeJson("evt-dup", "PRODUCT_LIKE_CHANGED", "2026-03-26T00:00:00Z", 101L, "UNLIKED").getBytes()
        );
        doThrow(new DataIntegrityViolationException("duplicate")).when(eventHandledJpaRepository).saveAndFlush(any());

        databaseService.processDb(record, parse(record.value()));

        verify(eventHandledJpaRepository).saveAndFlush(any());
        verify(productMetricsJpaRepository, never()).applyLikeDeltaIfNewer(any(), any(Long.class), any());
        verify(rankingWriteRepository, never()).upsertScore(any(), any(), any(Double.class), any());
    }

    @Test
    @DisplayName("PRODUCT_VIEWED는 view_count를 반영한다.")
    void processDb_whenProductViewed_shouldApplyViewDelta() {
        when(productViewContributionLimiter.allowContribution(any(), anyLong(), any())).thenReturn(true);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                0,
                1L,
                "201",
                envelopeJsonView("evt-view-1", "2026-03-26T00:00:00Z", 201L).getBytes()
        );

        databaseService.processDb(record, parse(record.value()));

        verify(productMetricsJpaRepository).applyViewDeltaIfNewer(
                eq(201L),
                eq(1L),
                eq(Instant.parse("2026-03-26T00:00:00Z"))
        );
    }

    @Test
    @DisplayName("PRODUCT_VIEWED 상한 초과면 view_count를 반영하지 않는다.")
    void processDb_whenProductViewedOverCap_shouldSkipViewDelta() {
        when(productViewContributionLimiter.allowContribution(eq("viewer-1"), eq(201L), any())).thenReturn(false);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                0,
                2L,
                "201",
                envelopeJsonView("evt-view-cap", "2026-03-26T00:00:00Z", 201L, "viewer-1").getBytes()
        );

        databaseService.processDb(record, parse(record.value()));

        verify(productMetricsJpaRepository, never()).applyViewDeltaIfNewer(any(), any(Long.class), any());
        verify(rankingWriteRepository, never()).upsertScore(any(), any(), any(Double.class), any());
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED는 주문 라인별 판매 수량을 반영한다.")
    void processDb_whenPaymentCompleted_shouldApplySoldDeltaPerLine() {
        when(productSoldContributionLimiter.allowContribution(eq("99"), anyLong(), anyLong(), any())).thenReturn(true);
        String json = "{\"eventId\":\"evt-pay-1\",\"eventType\":\"PAYMENT_COMPLETED\","
                + "\"occurredAt\":\"2026-03-26T00:00:00Z\",\"partitionKey\":\"99\","
                + "\"data\":{\"orderId\":99,\"lines\":[{\"productId\":301,\"quantity\":2},{\"productId\":302,\"quantity\":1}]}}";
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "order-events",
                0,
                1L,
                "99",
                json.getBytes()
        );

        databaseService.processDb(record, parse(record.value()));

        verify(productMetricsJpaRepository).applySoldDeltaIfNewer(
                eq(301L), eq(2L), eq(Instant.parse("2026-03-26T00:00:00Z")));
        verify(productMetricsJpaRepository).applySoldDeltaIfNewer(
                eq(302L), eq(1L), eq(Instant.parse("2026-03-26T00:00:00Z")));
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED 라인이 판매 기여 상한에 걸리면 해당 라인만 스킵한다.")
    void processDb_whenPaymentLineSoldCapped_shouldSkipThatLineOnly() {
        when(productSoldContributionLimiter.allowContribution(eq("buyer-1"), eq(301L), eq(2L), any())).thenReturn(false);
        when(productSoldContributionLimiter.allowContribution(eq("buyer-1"), eq(302L), eq(1L), any())).thenReturn(true);
        String json = "{\"eventId\":\"evt-pay-cap\",\"eventType\":\"PAYMENT_COMPLETED\","
                + "\"occurredAt\":\"2026-03-26T00:00:00Z\",\"partitionKey\":\"buyer-1\","
                + "\"data\":{\"orderId\":1,\"lines\":[{\"productId\":301,\"quantity\":2},{\"productId\":302,\"quantity\":1}]}}";
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "order-events",
                0,
                3L,
                "buyer-1",
                json.getBytes()
        );

        databaseService.processDb(record, parse(record.value()));

        verify(productMetricsJpaRepository, never()).applySoldDeltaIfNewer(eq(301L), any(Long.class), any());
        verify(productMetricsJpaRepository).applySoldDeltaIfNewer(
                eq(302L), eq(1L), eq(Instant.parse("2026-03-26T00:00:00Z")));
    }

    @Test
    @DisplayName("Redis 동기화 실패 시 DLQ 토픽으로 실패 이벤트를 발행한다.")
    void processDb_whenRankingSyncFails_shouldPublishDlqMessage() {
        ProductMetricsModel metrics = org.mockito.Mockito.mock(ProductMetricsModel.class);
        when(metrics.getProductId()).thenReturn(101L);
        when(metrics.getViewCount()).thenReturn(0L);
        when(metrics.getLikeCount()).thenReturn(1L);
        when(metrics.getSoldQuantity()).thenReturn(0L);
        when(productMetricsJpaRepository.findById(101L)).thenReturn(Optional.of(metrics));
        doThrow(new RuntimeException("redis down"))
                .when(rankingWriteRepository).upsertScore(any(), any(), any(Double.class), any());

        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "product-events",
                0,
                5L,
                "101",
                envelopeJson("evt-dlq-1", "PRODUCT_LIKE_CHANGED", "2026-03-26T00:00:00Z", 101L, "LIKED").getBytes()
        );

        databaseService.processDb(record, parse(record.value()));

        // E-LAG: DB 메트릭은 반영됐지만 Redis upsert 실패로 랭킹 반영이 지연/불일치될 수 있다.
        verify(eventHandledJpaRepository).saveAndFlush(any());
        verify(productMetricsJpaRepository).applyLikeDeltaIfNewer(
                eq(101L),
                eq(1L),
                eq(Instant.parse("2026-03-26T00:00:00Z"))
        );
        verify(rankingWriteRepository).upsertScore(any(), any(), any(Double.class), any());
        verify(kafkaTemplate).send(eq("product-events.DLQ"), any());
    }

    private ProductEventEnvelope parse(Object value) {
        try {
            byte[] bytes = value instanceof byte[] ? (byte[]) value : String.valueOf(value).getBytes();
            return new ObjectMapper().findAndRegisterModules().readValue(bytes, ProductEventEnvelope.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String envelopeJson(String eventId, String eventType, String occurredAt, Long productId, String action) {
        return "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"partitionKey\":\"" + productId + "\","
                + "\"data\":{"
                + "\"productId\":" + productId + ","
                + "\"action\":\"" + action + "\""
                + "}"
                + "}";
    }

    private static String envelopeJsonView(String eventId, String occurredAt, Long productId) {
        return envelopeJsonView(eventId, occurredAt, productId, String.valueOf(productId));
    }

    private static String envelopeJsonView(String eventId, String occurredAt, Long productId, String partitionKey) {
        return "{"
                + "\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"PRODUCT_VIEWED\","
                + "\"occurredAt\":\"" + occurredAt + "\","
                + "\"partitionKey\":\"" + partitionKey + "\","
                + "\"data\":{\"productId\":" + productId + "}"
                + "}";
    }
}
