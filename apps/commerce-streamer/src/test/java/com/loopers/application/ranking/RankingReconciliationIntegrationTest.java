package com.loopers.application.ranking;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.data.redis.core.RedisTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "collector.product.topic-name=product-events",
        "collector.order.topic-name=order-events",
        "collector.user.topic-name=user-events",
        "collector.product.dlq-suffix=.DLQ",
        "collector.lightweight-idempotency.redis-ttl-days=14",
        "collector.event-handled-cleanup.enabled=false",
        "collector.ranking-reconciliation.enabled=false",
        "spring.batch.job.enabled=false"
})
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@EmbeddedKafka(partitions = 1, topics = {
        "product-events", "product-events.DLQ",
        "order-events", "order-events.DLQ",
        "user-events", "user-events.DLQ"
})
class RankingReconciliationIntegrationTest {

    private static final String RANKING_KEY = "ranking:all:20260326";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RankingReconciliationService rankingReconciliationService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("보정 배치는 DB product_metrics를 기준으로 Redis ZSET 점수를 덮어쓴다.")
    void reconcileAll_shouldOverwriteRedisFromDbMetrics() {
        long productId = 9001L;
        Instant lastEvent = Instant.parse("2026-03-26T00:00:02Z");
        jdbcTemplate.update(
                """
                        INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity,
                            last_event_occurred_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, NOW(6))
                        """,
                productId,
                3L,
                0L,
                0L,
                Timestamp.from(lastEvent)
        );

        redisTemplate.opsForZSet().add(RANKING_KEY, String.valueOf(productId), 0.01d);

        rankingReconciliationService.reconcileAll();

        Double score = redisTemplate.opsForZSet().score(RANKING_KEY, String.valueOf(productId));
        assertThat(score).isEqualTo(0.6d);
    }
}
