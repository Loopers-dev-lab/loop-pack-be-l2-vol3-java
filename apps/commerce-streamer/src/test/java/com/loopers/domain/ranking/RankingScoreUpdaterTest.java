package com.loopers.domain.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.event.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(RedisTestContainersConfig.class)
class RankingScoreUpdaterTest {

    @Autowired
    private RankingScoreUpdater rankingScoreUpdater;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("조회 이벤트 시 viewWeight(0.1) 만큼 점수가 증가한다.")
    @Test
    void incrementView_addsViewWeight() {
        // act
        rankingScoreUpdater.incrementView(1L);

        // assert
        Double score = redisTemplate.opsForZSet().score(RankingKeyGenerator.keyOf(LocalDate.now()), "1");
        assertThat(score).isEqualTo(0.1);
    }

    @DisplayName("좋아요 이벤트 시 likeWeight(0.2) 만큼 점수가 증가한다.")
    @Test
    void incrementLike_addsLikeWeight() {
        // act
        rankingScoreUpdater.incrementLike(1L);

        // assert
        Double score = redisTemplate.opsForZSet().score(RankingKeyGenerator.keyOf(LocalDate.now()), "1");
        assertThat(score).isEqualTo(0.2);
    }

    @DisplayName("좋아요 취소 시 likeWeight(0.2) 만큼 점수가 감소한다.")
    @Test
    void decrementLike_subtractsLikeWeight() {
        // arrange
        rankingScoreUpdater.incrementLike(1L);
        rankingScoreUpdater.incrementLike(1L);

        // act
        rankingScoreUpdater.decrementLike(1L);

        // assert
        Double score = redisTemplate.opsForZSet().score(RankingKeyGenerator.keyOf(LocalDate.now()), "1");
        assertThat(score).isEqualTo(0.2);
    }

    @DisplayName("주문 이벤트 시 orderWeight * price * quantity 만큼 점수가 증가한다.")
    @Test
    void incrementOrder_addsWeightedOrderScore() {
        // act
        rankingScoreUpdater.incrementOrder(1L, 10000L, 2);

        // assert
        Double score = redisTemplate.opsForZSet().score(RankingKeyGenerator.keyOf(LocalDate.now()), "1");
        assertThat(score).isEqualTo(0.7 * 10000 * 2);
    }

    @DisplayName("같은 상품에 여러 이벤트가 누적되면 점수가 합산된다.")
    @Test
    void multipleEvents_accumulatesScore() {
        // act
        rankingScoreUpdater.incrementView(1L);   // 0.1
        rankingScoreUpdater.incrementLike(1L);   // 0.2
        rankingScoreUpdater.incrementOrder(1L, 5000L, 1);  // 0.7 * 5000

        // assert
        Double score = redisTemplate.opsForZSet().score(RankingKeyGenerator.keyOf(LocalDate.now()), "1");
        assertThat(score).isEqualTo(0.1 + 0.2 + 0.7 * 5000);
    }

    @DisplayName("랭킹 키의 TTL이 2일로 설정된다.")
    @Test
    void ttl_isSetToTwoDays() {
        // act
        rankingScoreUpdater.incrementView(1L);

        // assert
        Long ttl = redisTemplate.getExpire(RankingKeyGenerator.keyOf(LocalDate.now()));
        assertThat(ttl).isGreaterThan(86400L); // 1일(86400초) 초과
        assertThat(ttl).isLessThanOrEqualTo(172800L); // 2일(172800초) 이하
    }
}
