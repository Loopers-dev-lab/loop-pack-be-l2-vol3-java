package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.domain.ranking.RankingDeltaPending;
import com.loopers.domain.ranking.RankingDeltaPendingRepository;
import com.loopers.support.kafka.KafkaOutboxMessage;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
class ProductMetricsConsumerIntegrationTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ProductMetricsConsumer consumer;

    @Autowired
    private RankingDeltaPendingRepository rankingDeltaPendingRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String rankingKey(LocalDate date) {
        return "ranking:all:" + date.format(DATE_FORMAT);
    }

    private ConsumerRecord<Object, Object> buildRecord(String topic, String eventId, String eventType, Object payload)
        throws JsonProcessingException {
        KafkaOutboxMessage message = new KafkaOutboxMessage(
            eventId, eventType, objectMapper.writeValueAsString(payload), null
        );
        byte[] value = objectMapper.writeValueAsBytes(message);
        return new ConsumerRecord<>(topic, 0, 0L, null, value);
    }

    @DisplayName("batch 내 여러 이벤트가 집계되어 Redis에 한번에 반영된다.")
    @Test
    void consumeFlushesAggregatedScoresToRedis() throws JsonProcessingException {
        // arrange - 같은 상품에 view + like 이벤트
        String viewEventId = UUID.randomUUID().toString();
        String likeEventId = UUID.randomUUID().toString();
        List<ConsumerRecord<Object, Object>> records = List.of(
            buildRecord("product.view.events", viewEventId, "PRODUCT_VIEWED",
                new ProductMetricsService.ViewPayload(1L, "user1", "Mozilla")),
            buildRecord("product.like.events", likeEventId, "LIKE_CREATED",
                new ProductMetricsService.LikePayload(99L, 1L))
        );
        Acknowledgment ack = mock(Acknowledgment.class);

        // act
        consumer.consume(records, ack);

        // assert - Redis에 view(0.1) + like(0.2) = 0.3 합산
        Double score = redisTemplate.opsForZSet().score(rankingKey(LocalDate.now()), "1");
        assertThat(score).isNotNull().isCloseTo(0.3, Offset.offset(0.0001));

        // assert - pending delta가 FLUSHED 처리됨 (PENDING 조회 결과 없음)
        List<RankingDeltaPending> remaining = rankingDeltaPendingRepository
            .findPendingByEventIds(List.of(viewEventId, likeEventId));
        assertThat(remaining).isEmpty();

        // assert - flush 성공 후 ack 호출됨
        verify(ack).acknowledge();
    }

    @DisplayName("batch 내 서로 다른 상품의 이벤트는 각각의 ZSET 멤버에 반영된다.")
    @Test
    void consumeFlushesScoresForDifferentProducts() throws JsonProcessingException {
        // arrange
        String eventIdA = UUID.randomUUID().toString();
        String eventIdB = UUID.randomUUID().toString();
        List<ConsumerRecord<Object, Object>> records = List.of(
            buildRecord("product.view.events", eventIdA, "PRODUCT_VIEWED",
                new ProductMetricsService.ViewPayload(1L, "user1", "Mozilla")),
            buildRecord("product.like.events", eventIdB, "LIKE_CREATED",
                new ProductMetricsService.LikePayload(99L, 2L))
        );
        Acknowledgment ack = mock(Acknowledgment.class);

        // act
        consumer.consume(records, ack);

        // assert
        String key = rankingKey(LocalDate.now());
        assertThat(redisTemplate.opsForZSet().score(key, "1")).isCloseTo(0.1, Offset.offset(0.0001));
        assertThat(redisTemplate.opsForZSet().score(key, "2")).isCloseTo(0.2, Offset.offset(0.0001));
    }

    @DisplayName("flush 후 ZSET TTL이 2일로 설정된다.")
    @Test
    void consumeSetsTwoDayTtlAfterFlush() throws JsonProcessingException {
        // arrange
        String eventId = UUID.randomUUID().toString();
        List<ConsumerRecord<Object, Object>> records = List.of(
            buildRecord("product.view.events", eventId, "PRODUCT_VIEWED",
                new ProductMetricsService.ViewPayload(1L, "user1", "Mozilla"))
        );
        Acknowledgment ack = mock(Acknowledgment.class);

        // act
        consumer.consume(records, ack);

        // assert
        Long ttl = redisTemplate.getExpire(rankingKey(LocalDate.now()));
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(2 * 24 * 60 * 60L);
    }

    @DisplayName("동일 eventId가 재전달되면 Redis 점수는 중복 반영되지 않는다.")
    @Test
    void consumeDoesNotDoubleCountOnRedelivery() throws JsonProcessingException {
        // arrange - 같은 이벤트를 두 batch에서 수신 (재전달 시뮬레이션)
        String eventId = UUID.randomUUID().toString();
        ConsumerRecord<Object, Object> record = buildRecord("product.view.events", eventId, "PRODUCT_VIEWED",
            new ProductMetricsService.ViewPayload(1L, "user1", "Mozilla"));

        Acknowledgment ack1 = mock(Acknowledgment.class);
        Acknowledgment ack2 = mock(Acknowledgment.class);

        // act
        consumer.consume(List.of(record), ack1);
        consumer.consume(List.of(record), ack2); // 재전달

        // assert - 0.1 한 번만 반영
        Double score = redisTemplate.opsForZSet().score(rankingKey(LocalDate.now()), "1");
        assertThat(score).isCloseTo(0.1, Offset.offset(0.0001));

        verify(ack1).acknowledge();
        verify(ack2).acknowledge();
    }
}
