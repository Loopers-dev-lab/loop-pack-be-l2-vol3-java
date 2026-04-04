package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingRepositoryImplTest {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String TEST_KEY = "ranking:day:test";

    @BeforeEach
    void setUp() {
        redisTemplate.delete(TEST_KEY);
    }

    @DisplayName("Top-N 조회할 때, ")
    @Nested
    class GetTopN {

        @DisplayName("score 내림차순으로 반환한다.")
        @Test
        void returnsInDescendingOrder() {
            redisTemplate.opsForZSet().add(TEST_KEY, "101", 50.0);
            redisTemplate.opsForZSet().add(TEST_KEY, "102", 100.0);
            redisTemplate.opsForZSet().add(TEST_KEY, "103", 75.0);

            List<ProductRanking> result = rankingRepository.getTopN(TEST_KEY, 0, 2);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).productId()).isEqualTo(102L);
            assertThat(result.get(0).rank()).isEqualTo(1);
            assertThat(result.get(1).productId()).isEqualTo(103L);
            assertThat(result.get(1).rank()).isEqualTo(2);
            assertThat(result.get(2).productId()).isEqualTo(101L);
            assertThat(result.get(2).rank()).isEqualTo(3);
        }

        @DisplayName("페이징이 올바르게 동작한다.")
        @Test
        void paginatesCorrectly() {
            for (int i = 1; i <= 5; i++) {
                redisTemplate.opsForZSet().add(TEST_KEY, String.valueOf(i), i * 10.0);
            }

            List<ProductRanking> page1 = rankingRepository.getTopN(TEST_KEY, 0, 1);
            List<ProductRanking> page2 = rankingRepository.getTopN(TEST_KEY, 2, 3);

            assertThat(page1).hasSize(2);
            assertThat(page1.get(0).productId()).isEqualTo(5L);
            assertThat(page1.get(0).rank()).isEqualTo(1);

            assertThat(page2).hasSize(2);
            assertThat(page2.get(0).productId()).isEqualTo(3L);
            assertThat(page2.get(0).rank()).isEqualTo(3);
        }

        @DisplayName("키가 존재하지 않으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenKeyNotExists() {
            List<ProductRanking> result = rankingRepository.getTopN("nonexistent", 0, 9);

            assertThat(result).isEmpty();
        }
    }

    @DisplayName("개별 순위 조회할 때, ")
    @Nested
    class GetRank {

        @DisplayName("1-based 순위를 반환한다.")
        @Test
        void returnsOneBasedRank() {
            redisTemplate.opsForZSet().add(TEST_KEY, "101", 100.0);
            redisTemplate.opsForZSet().add(TEST_KEY, "102", 50.0);

            Optional<Long> rank = rankingRepository.getRank(TEST_KEY, 101L);

            assertThat(rank).isPresent().contains(1L);
        }

        @DisplayName("존재하지 않는 member이면 empty를 반환한다.")
        @Test
        void returnsEmpty_whenMemberNotExists() {
            redisTemplate.opsForZSet().add(TEST_KEY, "101", 100.0);

            Optional<Long> rank = rankingRepository.getRank(TEST_KEY, 999L);

            assertThat(rank).isEmpty();
        }
    }

    @DisplayName("전체 개수 조회할 때, ")
    @Nested
    class GetTotalCount {

        @DisplayName("ZSET의 member 수를 반환한다.")
        @Test
        void returnsMemberCount() {
            redisTemplate.opsForZSet().add(TEST_KEY, "101", 10.0);
            redisTemplate.opsForZSet().add(TEST_KEY, "102", 20.0);

            long count = rankingRepository.getTotalCount(TEST_KEY);

            assertThat(count).isEqualTo(2);
        }

        @DisplayName("키가 존재하지 않으면 0을 반환한다.")
        @Test
        void returnsZero_whenKeyNotExists() {
            long count = rankingRepository.getTotalCount("nonexistent");

            assertThat(count).isZero();
        }
    }
}
