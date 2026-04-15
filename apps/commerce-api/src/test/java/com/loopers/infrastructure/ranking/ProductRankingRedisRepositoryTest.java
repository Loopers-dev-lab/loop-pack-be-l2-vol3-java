package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankingInfo;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("ProductRankingRedisRepository 통합 테스트")
class ProductRankingRedisRepositoryTest {

    @Autowired
    private ProductRankingRedisRepository productRankingRedisRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final String DATE_KEY = "20260409";
    private static final String REDIS_KEY = "ranking:all:" + DATE_KEY;

    @BeforeEach
    void setUp() {
        masterRedisTemplate.opsForZSet().add(REDIS_KEY, "1", 30000.0);
        masterRedisTemplate.opsForZSet().add(REDIS_KEY, "2", 500.0);
        masterRedisTemplate.opsForZSet().add(REDIS_KEY, "3", 1.0);
        masterRedisTemplate.opsForZSet().add(REDIS_KEY, "4", 0.6);
        masterRedisTemplate.opsForZSet().add(REDIS_KEY, "5", 0.1);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("Top-N 랭킹을 score 내림차순으로 조회한다")
    void returnsTopNRankings() {
        // Act
        List<ProductRankingInfo> rankings = productRankingRedisRepository.getRankings(DATE_KEY, 0, 3);

        // Assert
        assertAll(
            () -> assertThat(rankings).hasSize(3),
            () -> assertThat(rankings.get(0).productId()).isEqualTo(1L),
            () -> assertThat(rankings.get(0).score()).isEqualTo(30000.0),
            () -> assertThat(rankings.get(0).rank()).isEqualTo(1L),
            () -> assertThat(rankings.get(1).productId()).isEqualTo(2L),
            () -> assertThat(rankings.get(1).rank()).isEqualTo(2L),
            () -> assertThat(rankings.get(2).productId()).isEqualTo(3L),
            () -> assertThat(rankings.get(2).rank()).isEqualTo(3L)
        );
    }

    @Test
    @DisplayName("페이징 offset이 적용된다")
    void appliesPagingOffset() {
        // Act
        List<ProductRankingInfo> rankings = productRankingRedisRepository.getRankings(DATE_KEY, 2, 2);

        // Assert
        assertAll(
            () -> assertThat(rankings).hasSize(2),
            () -> assertThat(rankings.get(0).productId()).isEqualTo(3L),
            () -> assertThat(rankings.get(0).rank()).isEqualTo(3L),
            () -> assertThat(rankings.get(1).productId()).isEqualTo(4L),
            () -> assertThat(rankings.get(1).rank()).isEqualTo(4L)
        );
    }

    @Test
    @DisplayName("특정 상품의 순위를 1-based로 반환한다")
    void returnsOneBasedRank() {
        // Act
        Long rank = productRankingRedisRepository.getRank(DATE_KEY, 2L);

        // Assert
        assertThat(rank).isEqualTo(2L);
    }

    @Test
    @DisplayName("랭킹에 없는 상품은 null을 반환한다")
    void returnsNullForUnrankedProduct() {
        // Act
        Long rank = productRankingRedisRepository.getRank(DATE_KEY, 999L);

        // Assert
        assertThat(rank).isNull();
    }

    @Test
    @DisplayName("특정 상품의 점수를 조회한다")
    void returnsScore() {
        // Act
        Double score = productRankingRedisRepository.getScore(DATE_KEY, 1L);

        // Assert
        assertThat(score).isEqualTo(30000.0);
    }

    @Test
    @DisplayName("존재하지 않는 상품의 점수는 null을 반환한다")
    void returnsNullScoreForUnrankedProduct() {
        // Act
        Double score = productRankingRedisRepository.getScore(DATE_KEY, 999L);

        // Assert
        assertThat(score).isNull();
    }

    @Test
    @DisplayName("전체 랭킹 수를 반환한다")
    void returnsTotalCount() {
        // Act
        Long count = productRankingRedisRepository.getTotalCount(DATE_KEY);

        // Assert
        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("빈 키에 대해 빈 결과를 반환한다")
    void returnsEmptyForNonExistentKey() {
        // Act
        List<ProductRankingInfo> rankings = productRankingRedisRepository.getRankings("99991231", 0, 10);
        Long rank = productRankingRedisRepository.getRank("99991231", 1L);
        Long count = productRankingRedisRepository.getTotalCount("99991231");

        // Assert
        assertAll(
            () -> assertThat(rankings).isEmpty(),
            () -> assertThat(rank).isNull(),
            () -> assertThat(count).isEqualTo(0L)
        );
    }
}
