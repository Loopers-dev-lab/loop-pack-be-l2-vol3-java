package com.loopers.domain.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.ranking.RankingDeltaPending;
import com.loopers.domain.ranking.RankingDeltaPendingRepository;
import com.loopers.domain.ranking.RankingDeltaPendingStatus;
import com.loopers.support.kafka.KafkaOutboxMessage;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ProductMetricsServiceRankingTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private ProductMetricsService productMetricsService;

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

    private KafkaOutboxMessage message(String eventType, Object payload) throws JsonProcessingException {
        return new KafkaOutboxMessage(
            UUID.randomUUID().toString(),
            eventType,
            objectMapper.writeValueAsString(payload),
            null
        );
    }

    @DisplayName("PRODUCT_VIEWED 이벤트 처리 시, ")
    @Nested
    class ViewEvent {

        @DisplayName("PENDING 상태의 RankingDeltaPending이 delta=0.1로 저장된다.")
        @Test
        void savesPendingDeltaForView() throws JsonProcessingException {
            // arrange
            String eventId = UUID.randomUUID().toString();
            KafkaOutboxMessage msg = new KafkaOutboxMessage(
                eventId, "PRODUCT_VIEWED",
                objectMapper.writeValueAsString(new ProductMetricsService.ViewPayload(1L, "user1", "Mozilla")),
                null
            );

            // act
            productMetricsService.handle(msg);

            // assert
            List<RankingDeltaPending> deltas = rankingDeltaPendingRepository.findPendingByEventIds(List.of(eventId));
            assertThat(deltas).hasSize(1);
            assertThat(deltas.get(0).getDelta()).isEqualTo(0.1);
            assertThat(deltas.get(0).getProductId()).isEqualTo(1L);
            assertThat(deltas.get(0).getStatus()).isEqualTo(RankingDeltaPendingStatus.PENDING);
        }

        @DisplayName("Redis에 직접 반영되지 않는다.")
        @Test
        void doesNotWriteToRedisDirectly() throws JsonProcessingException {
            // arrange
            KafkaOutboxMessage msg = message("PRODUCT_VIEWED",
                new ProductMetricsService.ViewPayload(1L, "user1", "Mozilla"));

            // act
            productMetricsService.handle(msg);

            // assert
            Double score = redisTemplate.opsForZSet().score(rankingKey(LocalDate.now()), "1");
            assertThat(score).isNull();
        }

        @DisplayName("중복 이벤트는 RankingDeltaPending을 추가 생성하지 않는다.")
        @Test
        void doesNotCreateDuplicatePending() throws JsonProcessingException {
            // arrange
            String eventId = UUID.randomUUID().toString();
            KafkaOutboxMessage msg = new KafkaOutboxMessage(
                eventId, "PRODUCT_VIEWED",
                objectMapper.writeValueAsString(new ProductMetricsService.ViewPayload(1L, "user1", "Mozilla")),
                null
            );

            // act
            productMetricsService.handle(msg);
            productMetricsService.handle(msg); // 동일 eventId

            // assert
            List<RankingDeltaPending> deltas = rankingDeltaPendingRepository.findPendingByEventIds(List.of(eventId));
            assertThat(deltas).hasSize(1);
        }
    }

    @DisplayName("LIKE_CREATED 이벤트 처리 시, ")
    @Nested
    class LikeCreatedEvent {

        @DisplayName("PENDING 상태의 RankingDeltaPending이 delta=0.2로 저장된다.")
        @Test
        void savesPendingDeltaForLike() throws JsonProcessingException {
            // arrange
            String eventId = UUID.randomUUID().toString();
            KafkaOutboxMessage msg = new KafkaOutboxMessage(
                eventId, "LIKE_CREATED",
                objectMapper.writeValueAsString(new ProductMetricsService.LikePayload(99L, 1L)),
                null
            );

            // act
            productMetricsService.handle(msg);

            // assert
            List<RankingDeltaPending> deltas = rankingDeltaPendingRepository.findPendingByEventIds(List.of(eventId));
            assertThat(deltas).hasSize(1);
            assertThat(deltas.get(0).getDelta()).isEqualTo(0.2);
            assertThat(deltas.get(0).getStatus()).isEqualTo(RankingDeltaPendingStatus.PENDING);
        }
    }

    @DisplayName("LIKE_DELETED 이벤트 처리 시, ")
    @Nested
    class LikeDeletedEvent {

        @DisplayName("PENDING 상태의 RankingDeltaPending이 delta=-0.2로 저장된다.")
        @Test
        void savesPendingDeltaForLikeDelete() throws JsonProcessingException {
            // arrange
            String eventId = UUID.randomUUID().toString();
            KafkaOutboxMessage msg = new KafkaOutboxMessage(
                eventId, "LIKE_DELETED",
                objectMapper.writeValueAsString(new ProductMetricsService.LikePayload(1L, 1L)),
                null
            );

            // act
            productMetricsService.handle(msg);

            // assert
            List<RankingDeltaPending> deltas = rankingDeltaPendingRepository.findPendingByEventIds(List.of(eventId));
            assertThat(deltas).hasSize(1);
            assertThat(deltas.get(0).getDelta()).isEqualTo(-0.2);
            assertThat(deltas.get(0).getStatus()).isEqualTo(RankingDeltaPendingStatus.PENDING);
        }
    }

    @DisplayName("PRODUCT_SOLD 이벤트 처리 시, ")
    @Nested
    class SoldEvent {

        @DisplayName("PENDING 상태의 RankingDeltaPending이 delta=0.6*log1p(amount)로 저장된다.")
        @Test
        void savesPendingDeltaForSold() throws JsonProcessingException {
            // arrange
            long amount = 10000L;
            String eventId = UUID.randomUUID().toString();
            KafkaOutboxMessage msg = new KafkaOutboxMessage(
                eventId, "PRODUCT_SOLD",
                objectMapper.writeValueAsString(new ProductMetricsService.ProductSoldPayload(1L, 999L, amount)),
                null
            );

            // act
            productMetricsService.handle(msg);

            // assert
            List<RankingDeltaPending> deltas = rankingDeltaPendingRepository.findPendingByEventIds(List.of(eventId));
            assertThat(deltas).hasSize(1);
            assertThat(deltas.get(0).getDelta()).isCloseTo(0.6 * Math.log1p(amount), org.assertj.core.data.Offset.offset(0.0001));
            assertThat(deltas.get(0).getStatus()).isEqualTo(RankingDeltaPendingStatus.PENDING);
        }
    }
}
