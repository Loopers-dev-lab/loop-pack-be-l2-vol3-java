package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RedisRankingRepository 통합 테스트")
class RedisRankingRepositoryIntegrationTest {

    private static final String KEY = "ranking:all:20260409";

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void seed(long productId, double score) {
        masterRedisTemplate.opsForZSet().add(KEY, String.valueOf(productId), score);
    }

    @Nested
    @DisplayName("getTopN")
    class GetTopN {

        @Test
        @DisplayName("내림차순으로 1-based rank 를 부여하여 반환")
        void descendingWithRank() {
            // given
            seed(1L, 10.0);
            seed(2L, 5.0);
            seed(3L, 1.0);

            // when
            List<RankingEntry> result = rankingRepository.getTopN(KEY, 1, 20);

            // then
            assertThat(result).hasSize(3);
            assertThat(result.get(0).productId()).isEqualTo(1L);
            assertThat(result.get(0).rank()).isEqualTo(1L);
            assertThat(result.get(1).productId()).isEqualTo(2L);
            assertThat(result.get(1).rank()).isEqualTo(2L);
            assertThat(result.get(2).productId()).isEqualTo(3L);
            assertThat(result.get(2).rank()).isEqualTo(3L);
        }

        @Test
        @DisplayName("page=2, size=2 는 3~4위 반환, rank 는 원 순위 유지")
        void pagination() {
            // given
            for (long i = 1; i <= 5; i++) {
                seed(i, 100.0 - i);
            }

            // when
            List<RankingEntry> page2 = rankingRepository.getTopN(KEY, 2, 2);

            // then
            assertThat(page2).hasSize(2);
            assertThat(page2.get(0).productId()).isEqualTo(3L);
            assertThat(page2.get(0).rank()).isEqualTo(3L);
            assertThat(page2.get(1).productId()).isEqualTo(4L);
            assertThat(page2.get(1).rank()).isEqualTo(4L);
        }

        @Test
        @DisplayName("존재하지 않는 키는 빈 리스트")
        void emptyKey() {
            assertThat(rankingRepository.getTopN("ranking:all:99991231", 1, 20)).isEmpty();
        }

        @Test
        @DisplayName("page 가 0 이하로 들어와도 1페이지로 보정")
        void pageClamp() {
            // given
            seed(1L, 10.0);

            // when
            List<RankingEntry> result = rankingRepository.getTopN(KEY, 0, 20);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rank()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("getRank")
    class GetRank {

        @Test
        @DisplayName("존재하는 멤버는 1-based 순위를 반환")
        void existingMember() {
            // given
            seed(1L, 10.0);
            seed(2L, 5.0);
            seed(3L, 1.0);

            // expect
            assertThat(rankingRepository.getRank(KEY, 1L)).isEqualTo(1L);
            assertThat(rankingRepository.getRank(KEY, 2L)).isEqualTo(2L);
            assertThat(rankingRepository.getRank(KEY, 3L)).isEqualTo(3L);
        }

        @Test
        @DisplayName("순위권 밖(키 없음) 이면 null")
        void absent() {
            assertThat(rankingRepository.getRank(KEY, 999L)).isNull();
        }
    }

    @Nested
    @DisplayName("getTotal")
    class GetTotal {

        @Test
        @DisplayName("ZCARD 결과를 반환")
        void total() {
            // given
            seed(1L, 1.0);
            seed(2L, 2.0);
            seed(3L, 3.0);

            // expect
            assertThat(rankingRepository.getTotal(KEY)).isEqualTo(3L);
        }

        @Test
        @DisplayName("존재하지 않는 키는 0")
        void empty() {
            assertThat(rankingRepository.getTotal("ranking:all:99991231")).isZero();
        }
    }
}
