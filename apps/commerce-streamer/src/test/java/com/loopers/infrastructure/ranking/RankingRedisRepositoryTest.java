package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.config.redis.RedisProperties;
import com.loopers.config.redis.RedisNodeInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankingRedisRepositoryTest {

    private RankingRedisRepository rankingRedisRepository;
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void setUp() {
        RedisProperties properties = new RedisProperties(
                0,
                new RedisNodeInfo("localhost", 6379),
                List.of(new RedisNodeInfo("localhost", 6380))
        );
        RedisConfig redisConfig = new RedisConfig(properties);

        var connectionFactory = redisConfig.masterRedisConnectionFactory();
        connectionFactory.afterPropertiesSet();

        redisTemplate = redisConfig.masterRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        rankingRedisRepository = new RankingRedisRepository(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("점수 증가")
    class IncrementScore {

        @Test
        @DisplayName("ZINCRBY로 점수가 증가한다")
        void incrementScore() {
            rankingRedisRepository.incrementScore("20260406", 1L, 0.1);

            Double score = redisTemplate.opsForZSet().score("ranking:all:20260406", "1");
            assertThat(score).isEqualTo(0.1);
        }

        @Test
        @DisplayName("동일 상품에 점수가 합산된다")
        void incrementScoreAccumulates() {
            rankingRedisRepository.incrementScore("20260406", 1L, 0.1);
            rankingRedisRepository.incrementScore("20260406", 1L, 0.2);

            Double score = redisTemplate.opsForZSet().score("ranking:all:20260406", "1");
            assertThat(score).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("서로 다른 상품은 각각 독립적으로 점수가 관리된다")
        void differentProductsIndependent() {
            rankingRedisRepository.incrementScore("20260406", 1L, 10.0);
            rankingRedisRepository.incrementScore("20260406", 2L, 5.0);

            Double score1 = redisTemplate.opsForZSet().score("ranking:all:20260406", "1");
            Double score2 = redisTemplate.opsForZSet().score("ranking:all:20260406", "2");
            assertThat(score1).isEqualTo(10.0);
            assertThat(score2).isEqualTo(5.0);
        }

        @Test
        @DisplayName("TTL이 2일로 설정된다")
        void ttlIsSet() {
            rankingRedisRepository.incrementScore("20260406", 1L, 0.1);

            Long ttl = redisTemplate.getExpire("ranking:all:20260406");
            assertThat(ttl).isGreaterThan(0);
            assertThat(ttl).isLessThanOrEqualTo(2 * 24 * 60 * 60);
        }
    }
}
