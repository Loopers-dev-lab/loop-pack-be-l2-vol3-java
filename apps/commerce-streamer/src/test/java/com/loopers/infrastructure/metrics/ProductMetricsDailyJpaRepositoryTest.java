package com.loopers.infrastructure.metrics;

import com.loopers.infrastructure.metrics.repository.ProductMetricsDailyJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false"
})
@Transactional
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class ProductMetricsDailyJpaRepositoryTest {

    @Autowired
    private ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 감소")
    @Nested
    class DecrementLikeCount {

        @DisplayName("좋아요 수가 1일 때 감소하면 score도 함께 차감된다")
        @Test
        void decrementScoreBeforeLikeCount() {
            LocalDate metricDate = LocalDate.of(2026, 4, 15);

            productMetricsDailyJpaRepository.incrementLikeCount(metricDate, 1L, 0.2d);
            productMetricsDailyJpaRepository.decrementLikeCount(metricDate, 1L, -0.2d);

            Long likeCount = jdbcTemplate.queryForObject(
                    "select like_count from product_metrics_daily where metric_date = ? and product_id = ?",
                    Long.class,
                    metricDate,
                    1L
            );
            Double score = jdbcTemplate.queryForObject(
                    "select score from product_metrics_daily where metric_date = ? and product_id = ?",
                    Double.class,
                    metricDate,
                    1L
            );

            assertAll(
                    () -> assertThat(likeCount).isEqualTo(0L),
                    () -> assertThat(score).isEqualTo(0.0d)
            );
        }
    }
}
