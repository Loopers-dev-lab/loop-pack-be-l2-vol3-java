package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisRankingRepositoryTest {

    private static final String TEST_KEY = "ranking:all:20250409";

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @BeforeEach
    void setUp() {
        redisTemplate.opsForZSet().add(TEST_KEY, "1", 100.0);
        redisTemplate.opsForZSet().add(TEST_KEY, "2", 300.0);
        redisTemplate.opsForZSet().add(TEST_KEY, "3", 200.0);
        redisTemplate.opsForZSet().add(TEST_KEY, "4", 50.0);
        redisTemplate.opsForZSet().add(TEST_KEY, "5", 150.0);
    }

    @DisplayName("Top-N 조회 시 점수 높은 순으로 반환한다.")
    @Test
    void getTopRankings_returnsInDescendingScoreOrder() {
        // act
        List<RankingEntry> result = rankingRepository.getTopRankings(TEST_KEY, 0, 3);

        // assert
        assertThat(result).hasSize(3);
        assertThat(result.get(0).productId()).isEqualTo(2L); // 300점
        assertThat(result.get(1).productId()).isEqualTo(3L); // 200점
        assertThat(result.get(2).productId()).isEqualTo(5L); // 150점
    }

    @DisplayName("Top-N 조회 시 rank가 offset부터 시작한다.")
    @Test
    void getTopRankings_rankStartsFromOffset() {
        // act
        List<RankingEntry> result = rankingRepository.getTopRankings(TEST_KEY, 0, 3);

        // assert
        assertThat(result.get(0).rank()).isEqualTo(0L);
        assertThat(result.get(1).rank()).isEqualTo(1L);
        assertThat(result.get(2).rank()).isEqualTo(2L);
    }

    @DisplayName("페이지네이션 — offset 지정 시 해당 위치부터 조회한다.")
    @Test
    void getTopRankings_withOffset() {
        // act — page 2 (offset=2, size=2)
        List<RankingEntry> result = rankingRepository.getTopRankings(TEST_KEY, 2, 2);

        // assert — 3위(150점), 4위(100점)
        assertThat(result).hasSize(2);
        assertThat(result.get(0).productId()).isEqualTo(5L);
        assertThat(result.get(1).productId()).isEqualTo(1L);
    }

    @DisplayName("존재하지 않는 키로 조회하면 빈 리스트를 반환한다.")
    @Test
    void getTopRankings_emptyKeyReturnsEmptyList() {
        // act
        List<RankingEntry> result = rankingRepository.getTopRankings("ranking:all:99991231", 0, 10);

        // assert
        assertThat(result).isEmpty();
    }

    @DisplayName("특정 상품의 순위를 조회한다. (0-based)")
    @Test
    void getRank_returns0BasedRank() {
        // act
        Long rank = rankingRepository.getRank(TEST_KEY, 2L); // 300점 → 0위

        // assert
        assertThat(rank).isEqualTo(0L);
    }

    @DisplayName("랭킹에 없는 상품 순위 조회 시 null을 반환한다.")
    @Test
    void getRank_returnsNullWhenNotInRanking() {
        // act
        Long rank = rankingRepository.getRank(TEST_KEY, 999L);

        // assert
        assertThat(rank).isNull();
    }

    @DisplayName("특정 상품의 점수를 조회한다.")
    @Test
    void getScore_returnsProductScore() {
        // act
        Double score = rankingRepository.getScore(TEST_KEY, 3L);

        // assert
        assertThat(score).isEqualTo(200.0);
    }
}
