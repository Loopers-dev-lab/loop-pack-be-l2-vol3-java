package com.loopers.integration.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.application.ranking.RankingSnapshotScheduler;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingSnapshot;
import com.loopers.infrastructure.ranking.persistence.RankingSnapshotJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class RankingSnapshotIntegrationTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String KEY_PREFIX = "ranking:v1:all:";

    @Autowired
    private RankingSnapshotScheduler rankingSnapshotScheduler;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RankingSnapshotJpaRepository rankingSnapshotJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("스냅샷을 수행할 때,")
    @Nested
    class TakeSnapshot {

        @DisplayName("Redis 상위 스코어가 DB에 저장된다.")
        @Test
        void savesTopScoresToDB() {
            // arrange
            String todayKey = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
            redisTemplate.opsForZSet().add(todayKey, "1", 45.3);
            redisTemplate.opsForZSet().add(todayKey, "2", 30.1);
            redisTemplate.opsForZSet().add(todayKey, "3", 15.0);

            // act
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            List<RankingSnapshot> snapshots = rankingSnapshotJpaRepository.findAll();
            assertAll(
                    () -> assertThat(snapshots).hasSize(3),
                    () -> assertThat(snapshots).extracting(RankingSnapshot::getProductId)
                            .containsExactlyInAnyOrder(1L, 2L, 3L),
                    () -> assertThat(snapshots).extracting(RankingSnapshot::getScoreDate)
                            .containsOnly(LocalDate.now())
            );
        }

        @DisplayName("두 번 호출하면 score가 최신 값으로 갱신된다.")
        @Test
        void updatesScore_whenCalledTwice() {
            // arrange
            String todayKey = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
            redisTemplate.opsForZSet().add(todayKey, "1", 10.0);

            rankingSnapshotScheduler.takeSnapshot();

            // act — score 변경 후 재스냅샷
            redisTemplate.opsForZSet().add(todayKey, "1", 50.0);
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            List<RankingSnapshot> snapshots = rankingSnapshotJpaRepository.findAll();
            assertAll(
                    () -> assertThat(snapshots).hasSize(1),
                    () -> assertThat(snapshots.get(0).getScore()).isEqualTo(50.0)
            );
        }

        @DisplayName("Redis에 100개 초과 상품이 있어도 Top 100만 저장된다.")
        @Test
        void savesOnlyTop100() {
            // arrange
            String todayKey = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);
            for (int i = 1; i <= 120; i++) {
                redisTemplate.opsForZSet().add(todayKey, String.valueOf(i), (double) i);
            }

            // act
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            List<RankingSnapshot> snapshots = rankingSnapshotJpaRepository.findAll();
            assertThat(snapshots).hasSize(100);

            // 상위 100개만 저장되므로 productId 21~120이 저장됨 (score 21~120)
            assertThat(snapshots).extracting(RankingSnapshot::getProductId)
                    .doesNotContain(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L,
                            11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L);
        }
    }
}
