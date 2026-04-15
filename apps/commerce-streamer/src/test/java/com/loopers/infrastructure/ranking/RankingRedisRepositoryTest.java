package com.loopers.infrastructure.ranking;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RankingRedisRepository 통합 테스트")
class RankingRedisRepositoryTest {

    @Autowired
    private RankingRedisRepository rankingRedisRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("ZINCRBY로 점수가 누적된다")
    void incrementsScore() {
        // Arrange
        String dateKey = "20260407";
        Long productId = 1L;

        // Act
        rankingRedisRepository.incrementScore(dateKey, productId, 0.1);
        rankingRedisRepository.incrementScore(dateKey, productId, 0.2);

        // Assert
        Double score = masterRedisTemplate.opsForZSet().score("ranking:all:20260407", "1");
        assertThat(score).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("서로 다른 상품의 점수가 독립적으로 관리된다")
    void managesScoresIndependently() {
        // Arrange
        String dateKey = "20260407";

        // Act
        rankingRedisRepository.incrementScore(dateKey, 1L, 10.0);
        rankingRedisRepository.incrementScore(dateKey, 2L, 20.0);

        // Assert
        Double score1 = masterRedisTemplate.opsForZSet().score("ranking:all:20260407", "1");
        Double score2 = masterRedisTemplate.opsForZSet().score("ranking:all:20260407", "2");
        assertThat(score1).isEqualTo(10.0);
        assertThat(score2).isEqualTo(20.0);
    }

    @Test
    @DisplayName("TTL이 2일로 설정된다")
    void setsTtlToTwoDays() {
        // Arrange
        String dateKey = "20260407";

        // Act
        rankingRedisRepository.incrementScore(dateKey, 1L, 1.0);

        // Assert
        Long ttl = masterRedisTemplate.getExpire("ranking:all:20260407", TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(2 * 24 * 60 * 60); // 2일 이하
    }

    @Test
    @DisplayName("날짜별로 키가 분리된다")
    void separatesKeysByDate() {
        // Act
        rankingRedisRepository.incrementScore("20260406", 1L, 100.0);
        rankingRedisRepository.incrementScore("20260407", 1L, 50.0);

        // Assert
        Double scoreYesterday = masterRedisTemplate.opsForZSet().score("ranking:all:20260406", "1");
        Double scoreToday = masterRedisTemplate.opsForZSet().score("ranking:all:20260407", "1");
        assertThat(scoreYesterday).isEqualTo(100.0);
        assertThat(scoreToday).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Score Carry-Over 시 가중치가 적용되어 이월된다")
    void carriesOverScoresWithWeight() {
        // Arrange
        rankingRedisRepository.incrementScore("20260409", 1L, 30000.0);
        rankingRedisRepository.incrementScore("20260409", 2L, 500.0);
        rankingRedisRepository.incrementScore("20260409", 3L, 1.0);

        // Act
        rankingRedisRepository.carryOverScores("20260409", "20260410", 0.1);

        // Assert
        Double score1 = masterRedisTemplate.opsForZSet().score("ranking:all:20260410", "1");
        Double score2 = masterRedisTemplate.opsForZSet().score("ranking:all:20260410", "2");
        Double score3 = masterRedisTemplate.opsForZSet().score("ranking:all:20260410", "3");
        assertThat(score1).isCloseTo(3000.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(score2).isCloseTo(50.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(score3).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("Score Carry-Over 후 TTL이 설정된다")
    void setsTtlAfterCarryOver() {
        // Arrange
        rankingRedisRepository.incrementScore("20260409", 1L, 100.0);

        // Act
        rankingRedisRepository.carryOverScores("20260409", "20260410", 0.1);

        // Assert
        Long ttl = masterRedisTemplate.getExpire("ranking:all:20260410", TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(2 * 24 * 60 * 60);
    }

    @Test
    @DisplayName("원본 키의 점수는 변하지 않는다")
    void doesNotModifySourceKey() {
        // Arrange
        rankingRedisRepository.incrementScore("20260409", 1L, 100.0);

        // Act
        rankingRedisRepository.carryOverScores("20260409", "20260410", 0.1);

        // Assert
        Double originalScore = masterRedisTemplate.opsForZSet().score("ranking:all:20260409", "1");
        assertThat(originalScore).isEqualTo(100.0);
    }
}
