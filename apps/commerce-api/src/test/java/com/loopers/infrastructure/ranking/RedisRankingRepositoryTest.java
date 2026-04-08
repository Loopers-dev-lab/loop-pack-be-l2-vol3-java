package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(RedisTestContainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RedisRankingRepositoryTest {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("findProductIdsByRank() 를 호출할 때, ")
    @Nested
    class FindProductIdsByRank {

        @DisplayName("점수가 높은 순서대로 productId 목록을 반환한다.")
        @Test
        void returnsProductIds_inDescendingScoreOrder() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "10", 3.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "20", 5.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "30", 1.0);

            // act
            List<Long> result = rankingRepository.findProductIdsByRank(date, 0, 3);

            // assert
            assertThat(result).containsExactly(20L, 10L, 30L);
        }

        @DisplayName("offset과 limit 에 따라 페이지 범위만 반환한다.")
        @Test
        void returnsPagedResult_givenOffsetAndLimit() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "1", 5.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "2", 4.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "3", 3.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "4", 2.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "5", 1.0);

            // act
            List<Long> result = rankingRepository.findProductIdsByRank(date, 2, 2);

            // assert
            assertThat(result).containsExactly(3L, 4L);
        }

        @DisplayName("해당 날짜의 ZSET 이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoZSetForDate() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);

            // act
            List<Long> result = rankingRepository.findProductIdsByRank(date, 0, 20);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("countByDate() 를 호출할 때, ")
    @Nested
    class CountByDate {

        @DisplayName("ZSET 의 총 member 수를 반환한다.")
        @Test
        void returnsTotalMemberCount() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "10", 1.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "20", 2.0);
            redisTemplate.opsForZSet().add("ranking:all:20260408", "30", 3.0);

            // act
            long count = rankingRepository.countByDate(date);

            // assert
            assertThat(count).isEqualTo(3);
        }

        @DisplayName("해당 날짜의 ZSET 이 없으면 0을 반환한다.")
        @Test
        void returnsZero_whenNoZSetForDate() {
            // arrange
            LocalDate date = LocalDate.of(2026, 4, 8);

            // act
            long count = rankingRepository.countByDate(date);

            // assert
            assertThat(count).isZero();
        }
    }
}
