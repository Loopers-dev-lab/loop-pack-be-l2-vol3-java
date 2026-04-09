package com.loopers.infrastructure.ranking.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.BaseIntegrationTest;

@DisplayName("RedisRankingRepository 통합 테스트")
class RedisRankingRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String KEY = "ranking:v1:all:20250406";

    @BeforeEach
    void setUp() {
        redisTemplate.opsForZSet().add(KEY, "1", 70.0);
        redisTemplate.opsForZSet().add(KEY, "2", 58.4);
        redisTemplate.opsForZSet().add(KEY, "3", 45.2);
        redisTemplate.opsForZSet().add(KEY, "4", 30.0);
        redisTemplate.opsForZSet().add(KEY, "5", 15.5);
    }

    @DisplayName("상위 랭킹을 조회할 때,")
    @Nested
    class ReadTopRanked {

        @DisplayName("score 내림차순으로 정렬되고, rank는 1-based로 반환된다.")
        @Test
        void returnsItemsInDescendingScoreOrder() {
            // act
            List<RankingItem> items = rankingRepository.readTopRanked(KEY, 0, 3);

            // assert
            assertAll(
                    () -> assertThat(items).hasSize(3),
                    () -> assertThat(items.get(0)).isEqualTo(new RankingItem(1, 1L, 70.0)),
                    () -> assertThat(items.get(1)).isEqualTo(new RankingItem(2, 2L, 58.4)),
                    () -> assertThat(items.get(2)).isEqualTo(new RankingItem(3, 3L, 45.2))
            );
        }

        @DisplayName("offset을 지정하면, 해당 위치부터 조회하고 rank가 offset 기반으로 계산된다.")
        @Test
        void returnsItemsFromOffset() {
            // act
            List<RankingItem> items = rankingRepository.readTopRanked(KEY, 2, 2);

            // assert
            assertAll(
                    () -> assertThat(items).hasSize(2),
                    () -> assertThat(items.get(0)).isEqualTo(new RankingItem(3, 3L, 45.2)),
                    () -> assertThat(items.get(1)).isEqualTo(new RankingItem(4, 4L, 30.0))
            );
        }

        @DisplayName("데이터가 없으면, 빈 리스트를 반환한다.")
        @Test
        void returnsEmptyList_whenNoData() {
            // act
            List<RankingItem> items = rankingRepository.readTopRanked("ranking:v1:all:99990101", 0, 10);

            // assert
            assertThat(items).isEmpty();
        }
    }

    @DisplayName("특정 상품의 순위를 조회할 때,")
    @Nested
    class FindRank {

        @DisplayName("존재하는 상품이면, 1-based 순위를 반환한다.")
        @Test
        void returnsOneBased_whenProductExists() {
            // act
            Integer rank = rankingRepository.findRank(KEY, 2L);

            // assert
            assertThat(rank).isEqualTo(2);
        }

        @DisplayName("1위 상품이면, 1을 반환한다.")
        @Test
        void returnsOne_whenTopRanked() {
            // act
            Integer rank = rankingRepository.findRank(KEY, 1L);

            // assert
            assertThat(rank).isEqualTo(1);
        }

        @DisplayName("존재하지 않는 상품이면, null을 반환한다.")
        @Test
        void returnsNull_whenProductNotFound() {
            // act
            Integer rank = rankingRepository.findRank(KEY, 999L);

            // assert
            assertThat(rank).isNull();
        }
    }
}
