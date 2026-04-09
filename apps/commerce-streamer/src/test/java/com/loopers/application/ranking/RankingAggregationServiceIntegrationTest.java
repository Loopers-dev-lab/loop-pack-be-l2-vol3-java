package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductMetricsHourlyRepository;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka 없이 `RankingAggregationService.processCatalogBatch / processOrderBatch` 를 직접 호출해
 * DB + Redis 통합 동작을 검증한다. (Kafka 리스너 레이어는 단위 테스트에서 확인)
 *
 * <p>Clock 은 테스트 환경에서 시스템 시각을 사용 — 오늘 키가 자연스럽게 잡힌다.
 */
@SpringBootTest
@DisplayName("RankingAggregationService 통합 테스트")
class RankingAggregationServiceIntegrationTest {

    @Autowired
    private RankingAggregationService service;

    @Autowired
    private ProductMetricsHourlyRepository metricsRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private ConsumerRecord<String, String> rec(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", value);
    }

    @Nested
    @DisplayName("processCatalogBatch — DB + Redis end-to-end")
    class Catalog {

        @Test
        @DisplayName("view/like 이벤트가 DB UPSERT + ZSET ZADD 까지 전파된다")
        void viewAndLikeEndToEnd() {
            // given
            List<ConsumerRecord<String, String>> records = List.of(
                    rec("catalog-events", """
                            {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """),
                    rec("catalog-events", """
                            {"eventId":"v2","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """),
                    rec("catalog-events", """
                            {"eventId":"l1","eventType":"PRODUCT_LIKED","data":{"productId":1,"liked":true}}
                            """)
            );

            // when
            service.processCatalogBatch(records);

            // then — DB 반영
            var snap = metricsRepository.snapshotByDate(1L, LocalDate.now());
            assertThat(snap.totalView()).isEqualTo(2);
            assertThat(snap.totalLike()).isEqualTo(1);

            // then — ZSET 반영 (오늘 키)
            String todayKey = RankingKey.daily(LocalDate.now());
            Double score = masterRedisTemplate.opsForZSet().score(todayKey, "1");
            assertThat(score).isNotNull().isGreaterThan(0.0);
        }

        @Test
        @DisplayName("동일 eventId 로 두 번 호출해도 중복 반영되지 않는다 (멱등)")
        void idempotency() {
            // given
            List<ConsumerRecord<String, String>> records = List.of(
                    rec("catalog-events", """
                            {"eventId":"dup","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """)
            );

            // when — 같은 배치를 2번 처리
            service.processCatalogBatch(records);
            service.processCatalogBatch(records);

            // then
            var snap = metricsRepository.snapshotByDate(1L, LocalDate.now());
            assertThat(snap.totalView()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("processOrderBatch — 가중치 검증")
    class Order {

        @Test
        @DisplayName("주문 1건(10000원) 이 좋아요 3건 보다 높은 점수를 가진다 (checklist)")
        void orderBeatsLikes() {
            // given — 상품1: 좋아요 3건 / 상품2: 주문 1건 10000원
            List<ConsumerRecord<String, String>> likeRecords = List.of(
                    rec("catalog-events", """
                            {"eventId":"l1","eventType":"PRODUCT_LIKED","data":{"productId":1,"liked":true}}
                            """),
                    rec("catalog-events", """
                            {"eventId":"l2","eventType":"PRODUCT_LIKED","data":{"productId":1,"liked":true}}
                            """),
                    rec("catalog-events", """
                            {"eventId":"l3","eventType":"PRODUCT_LIKED","data":{"productId":1,"liked":true}}
                            """)
            );
            List<ConsumerRecord<String, String>> orderRecords = List.of(
                    rec("order-events", """
                            {"eventId":"o1","eventType":"ORDER_PAID","data":{"orderedProducts":[
                              {"productId":2,"quantity":1,"unitPrice":10000}
                            ]}}
                            """)
            );

            // when
            service.processCatalogBatch(likeRecords);
            service.processOrderBatch(orderRecords);

            // then
            String todayKey = RankingKey.daily(LocalDate.now());
            Set<ZSetOperations.TypedTuple<String>> top =
                    masterRedisTemplate.opsForZSet().reverseRangeWithScores(todayKey, 0, -1);
            assertThat(top).isNotNull().isNotEmpty();

            Double orderScore = masterRedisTemplate.opsForZSet().score(todayKey, "2");
            Double likeScore = masterRedisTemplate.opsForZSet().score(todayKey, "1");
            assertThat(orderScore).isNotNull();
            assertThat(likeScore).isNotNull();
            assertThat(orderScore).isGreaterThan(likeScore);

            // ZREVRANK — 1위는 주문 상품(id=2)
            Long rankOfOrder = masterRedisTemplate.opsForZSet().reverseRank(todayKey, "2");
            assertThat(rankOfOrder).isEqualTo(0L); // 0-based 에서 0 == 1위
        }

        @Test
        @DisplayName("ZSET 키에 retention TTL 이 설정된다 (2 일)")
        void retentionSet() {
            // given
            List<ConsumerRecord<String, String>> records = List.of(
                    rec("catalog-events", """
                            {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """)
            );

            // when
            service.processCatalogBatch(records);

            // then — TTL 이 0 초과 2일 이하로 설정되어 있어야 함
            String todayKey = RankingKey.daily(LocalDate.now());
            Long ttlSeconds = masterRedisTemplate.getExpire(todayKey);
            assertThat(ttlSeconds).isNotNull().isPositive()
                    .isLessThanOrEqualTo(2L * 24 * 60 * 60);
        }
    }
}
