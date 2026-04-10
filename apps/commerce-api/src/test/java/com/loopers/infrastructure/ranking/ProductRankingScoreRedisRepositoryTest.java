package com.loopers.infrastructure.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.ProductRankingScoreModel;
import com.loopers.utils.RedisCleanUp;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@DisplayName("ProductRankingScoreRedisRepository 통합 테스트")
@SpringBootTest
class ProductRankingScoreRedisRepositoryTest {

    @Autowired
    private ProductRankingScoreRedisRepository redisRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 7);
    private static final String KEY = "ranking:all:20260407";

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    private void addScore(Long productId, double score) {
        redisTemplate.opsForZSet().add(KEY, String.valueOf(productId), score);
    }

    @DisplayName("ZREVRANGE 랭킹 목록 조회")
    @Nested
    class FindTopByDate {

        @DisplayName("점수 높은 순으로 정렬되어 반환된다.")
        @Test
        void returnsOrderedByScoreDesc() {
            addScore(1L, 100.0);
            addScore(2L, 300.0);
            addScore(3L, 200.0);

            List<ProductRankingScoreModel> result = redisRepository
                    .findTopByDate(TODAY, 0, 2);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getProductId()).isEqualTo(2L);
            assertThat(result.get(0).getScore()).isCloseTo(300.0, within(0.001));
            assertThat(result.get(1).getProductId()).isEqualTo(3L);
            assertThat(result.get(2).getProductId()).isEqualTo(1L);
        }

        @DisplayName("start/end로 페이지네이션이 동작한다.")
        @Test
        void paginationWorks() {
            for (long i = 1; i <= 10; i++) {
                addScore(i, i * 10.0);
            }

            List<ProductRankingScoreModel> page2 = redisRepository
                    .findTopByDate(TODAY, 3, 5);

            assertThat(page2).hasSize(3);
            assertThat(page2.get(0).getProductId()).isEqualTo(7L);
        }

        @DisplayName("데이터가 없으면 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyWhenNoData() {
            List<ProductRankingScoreModel> result = redisRepository
                    .findTopByDate(TODAY, 0, 19);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("ZCARD 전체 수 조회")
    @Nested
    class CountByDate {

        @DisplayName("등록된 상품 수를 반환한다.")
        @Test
        void returnsCount() {
            addScore(1L, 100.0);
            addScore(2L, 200.0);
            addScore(3L, 300.0);

            assertThat(redisRepository.countByDate(TODAY)).isEqualTo(3);
        }

        @DisplayName("데이터가 없으면 0을 반환한다.")
        @Test
        void returnsZeroWhenEmpty() {
            assertThat(redisRepository.countByDate(TODAY)).isZero();
        }
    }

    @DisplayName("ZREVRANK 순위 조회")
    @Nested
    class FindRankByProductId {

        @DisplayName("1-based 순위를 반환한다.")
        @Test
        void returnsOneBasedRank() {
            addScore(1L, 100.0);
            addScore(2L, 300.0);
            addScore(3L, 200.0);

            assertThat(redisRepository.findRankByProductId(2L, TODAY))
                    .isPresent().hasValue(1L);
            assertThat(redisRepository.findRankByProductId(3L, TODAY))
                    .isPresent().hasValue(2L);
            assertThat(redisRepository.findRankByProductId(1L, TODAY))
                    .isPresent().hasValue(3L);
        }

        @DisplayName("존재하지 않는 상품이면 빈 값을 반환한다.")
        @Test
        void returnsEmptyWhenNotFound() {
            assertThat(redisRepository.findRankByProductId(999L, TODAY))
                    .isEmpty();
        }
    }
}
