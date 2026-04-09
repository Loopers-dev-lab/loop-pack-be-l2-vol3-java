package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loopers.application.collector.CouponIssueConsumeService;
import com.loopers.application.collector.EventDedupService;
import com.loopers.application.collector.ProductMetricsAggregationService;
import com.loopers.application.collector.RankingKeyGenerator;
import com.loopers.application.collector.RealtimeRankingAggregationService;
import com.loopers.kafka.message.KafkaEventEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingAggregationE2ETest {

    private CommerceEventKafkaConsumer consumer;
    private EventDedupService eventDedupService;
    private ObjectMapper objectMapper;
    private ZSetOperations<String, String> zSetOperations;
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        eventDedupService = mock(EventDedupService.class);
        when(eventDedupService.markIfNotHandled(any(), any())).thenReturn(true);

        ProductMetricsAggregationService productMetricsAggregationService = mock(ProductMetricsAggregationService.class);
        CouponIssueConsumeService couponIssueConsumeService = mock(CouponIssueConsumeService.class);

        redisTemplate = mock(RedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        RankingKeyGenerator rankingKeyGenerator = new RankingKeyGenerator("Asia/Seoul");
        RealtimeRankingAggregationService realtimeRankingAggregationService =
            new RealtimeRankingAggregationService(rankingKeyGenerator, redisTemplate);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "viewWeight", 0.1);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "likeWeight", 0.2);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "orderWeight", 0.6);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "dwellWeight", 0.3);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "dwellMinSeconds", 5);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "dwellMaxSeconds", 600);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "dwellDedupTtlMinutes", 30L);
        ReflectionTestUtils.setField(realtimeRankingAggregationService, "ttlDays", 2L);

        // dwell dedup - setIfAbsent returns true (new entry)
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        consumer = new CommerceEventKafkaConsumer(
            objectMapper,
            eventDedupService,
            productMetricsAggregationService,
            realtimeRankingAggregationService,
            couponIssueConsumeService
        );
        ReflectionTestUtils.setField(consumer, "metricsConsumerGroup", "commerce-metrics-consumer");
    }

    @DisplayName("이벤트 소비 후 Redis ZSET에 가중치 기반 점수가 올바르게 반영된다 - 주문 1건 > 좋아요 3건")
    @Test
    void consumesEventsAndAppliesWeightedScoresToRedisZSet() throws Exception {
        // arrange
        Long productA = 1L;
        Long productB = 2L;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        // act - VIEW: productA 3회
        for (int i = 0; i < 3; i++) {
            processEvent("PRODUCT_VIEWED", Map.of("productId", productA), now);
        }

        // act - LIKE: productA 좋아요 2회
        processEvent("PRODUCT_LIKE_CHANGED", Map.of("productId", productA, "delta", 1L), now);
        processEvent("PRODUCT_LIKE_CHANGED", Map.of("productId", productA, "delta", 1L), now);

        // act - ORDER: productB 주문 1건 (unitPrice=10000, quantity=1)
        processEvent("ORDER_PLACED", Map.of(
            "items", List.of(Map.of("productId", productB, "unitPrice", 10000L, "quantity", 1L))
        ), now);

        // act - DWELL: productB 체류 60초
        processEvent("PRODUCT_DWELLED", Map.of(
            "productId", productB, "userId", 100L, "dwellTimeSeconds", 60
        ), now);

        // assert - productA: VIEW 3 * 0.1 = 0.3, LIKE 2 * 0.2 = 0.4
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        verify(zSetOperations, atLeastOnce()).incrementScore(keyCaptor.capture(), memberCaptor.capture(), scoreCaptor.capture());

        double totalScoreA = 0.0;
        double totalScoreB = 0.0;
        List<String> keys = keyCaptor.getAllValues();
        List<String> members = memberCaptor.getAllValues();
        List<Double> scores = scoreCaptor.getAllValues();

        for (int i = 0; i < members.size(); i++) {
            // 일간 키 기준으로만 기대값 검증 (시간별 키는 동일 점수로 별도 누적)
            if (keys.get(i).contains(":hour:")) {
                continue;
            }
            if (members.get(i).equals(String.valueOf(productA))) {
                totalScoreA += scores.get(i);
            } else if (members.get(i).equals(String.valueOf(productB))) {
                totalScoreB += scores.get(i);
            }
        }

        // productA: 3*0.1 + 2*0.2 = 0.7
        assertThat(totalScoreA).isCloseTo(0.7, org.assertj.core.data.Offset.offset(0.001));

        // productB: 10000*1*0.6 + log10(60)*0.3 = 6000 + ~0.533
        double expectedOrderScore = 10000 * 1 * 0.6;
        double expectedDwellScore = Math.log10(60) * 0.3;
        assertThat(totalScoreB).isCloseTo(expectedOrderScore + expectedDwellScore, org.assertj.core.data.Offset.offset(0.001));

        // 주문 1건의 점수(6000+)가 좋아요+조회(0.7)보다 훨씬 크다
        assertThat(totalScoreB).isGreaterThan(totalScoreA);
    }

    @DisplayName("4가지 이벤트 타입이 모두 처리되어 ZSET에 반영된다")
    @Test
    void allFourEventTypesAreProcessed() throws Exception {
        // arrange
        Long productId = 10L;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));

        // act - VIEW
        processEvent("PRODUCT_VIEWED", Map.of("productId", productId), now);
        // act - LIKE
        processEvent("PRODUCT_LIKE_CHANGED", Map.of("productId", productId, "delta", 1L), now);
        // act - ORDER
        processEvent("ORDER_PLACED", Map.of(
            "items", List.of(Map.of("productId", productId, "unitPrice", 5000L, "quantity", 2L))
        ), now);
        // act - DWELL
        processEvent("PRODUCT_DWELLED", Map.of(
            "productId", productId, "userId", 200L, "dwellTimeSeconds", 30
        ), now);

        // assert - 4가지 이벤트 모두 incrementScore 호출
        ArgumentCaptor<Double> scoreCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(zSetOperations, atLeastOnce()).incrementScore(keyCaptor.capture(), eq(String.valueOf(productId)), scoreCaptor.capture());

        List<String> keys = keyCaptor.getAllValues();
        List<Double> scores = scoreCaptor.getAllValues();
        assertThat(scores).hasSize(8);

        long dailyCount = keys.stream().filter(key -> !key.contains(":hour:")).count();
        long hourlyCount = keys.stream().filter(key -> key.contains(":hour:")).count();
        assertThat(dailyCount).isEqualTo(4L);
        assertThat(hourlyCount).isEqualTo(4L);

        // VIEW: 0.1, LIKE: 0.2, ORDER: 5000*2*0.6=6000, DWELL: log10(30)*0.3
        assertThat(scores.stream().anyMatch(score -> Math.abs(score - 0.1) < 0.001)).isTrue();
        assertThat(scores.stream().anyMatch(score -> Math.abs(score - 0.2) < 0.001)).isTrue();
        assertThat(scores.stream().anyMatch(score -> Math.abs(score - 6000.0) < 0.001)).isTrue();
        assertThat(scores.stream().anyMatch(score -> Math.abs(score - (Math.log10(30) * 0.3)) < 0.001)).isTrue();
    }

    private void processEvent(String eventType, Map<String, Object> payload, ZonedDateTime occurredAt) throws Exception {
        KafkaEventEnvelope envelope = new KafkaEventEnvelope(
            UUID.randomUUID().toString(),
            eventType,
            "PRODUCT",
            "1",
            "1",
            1,
            occurredAt,
            payload
        );

        String payloadStr = objectMapper.writeValueAsString(envelope);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
            "catalog-events", 0, 0L, "1", payloadStr
        );

        consumer.processMetricsRecord(record);
    }
}
