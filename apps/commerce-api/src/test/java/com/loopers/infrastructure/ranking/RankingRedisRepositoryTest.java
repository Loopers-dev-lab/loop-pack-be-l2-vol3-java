package com.loopers.infrastructure.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.config.redis.RedisNodeInfo;
import com.loopers.config.redis.RedisProperties;
import com.loopers.domain.ranking.RankingRepository;
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

        var connectionFactory = redisConfig.defaultRedisConnectionFactory();
        connectionFactory.afterPropertiesSet();

        redisTemplate = redisConfig.defaultRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        rankingRedisRepository = new RankingRedisRepository(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Nested
    @DisplayName("랭킹 조회")
    class GetTopRankings {

        @Test
        @DisplayName("ZREVRANGE로 점수 내림차순으로 상위 N개를 조회한다")
        void getTopRankings() {
            redisTemplate.opsForZSet().add("ranking:all:20260406", "1", 100.0);
            redisTemplate.opsForZSet().add("ranking:all:20260406", "2", 200.0);
            redisTemplate.opsForZSet().add("ranking:all:20260406", "3", 50.0);

            List<RankingRepository.ProductScore> result =
                    rankingRedisRepository.getTopRankings("20260406", 0, 2);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).productId()).isEqualTo(2L);
            assertThat(result.get(0).score()).isEqualTo(200.0);
            assertThat(result.get(1).productId()).isEqualTo(1L);
            assertThat(result.get(1).score()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("페이지네이션 offset이 적용된다")
        void getTopRankingsWithOffset() {
            redisTemplate.opsForZSet().add("ranking:all:20260406", "1", 100.0);
            redisTemplate.opsForZSet().add("ranking:all:20260406", "2", 200.0);
            redisTemplate.opsForZSet().add("ranking:all:20260406", "3", 50.0);

            List<RankingRepository.ProductScore> result =
                    rankingRedisRepository.getTopRankings("20260406", 1, 2);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).productId()).isEqualTo(1L);
            assertThat(result.get(1).productId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("데이터가 없으면 빈 리스트를 반환한다")
        void emptyResult() {
            List<RankingRepository.ProductScore> result =
                    rankingRedisRepository.getTopRankings("20260406", 0, 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("순위 조회")
    class GetRank {

        @Test
        @DisplayName("ZREVRANK로 특정 상품의 순위를 조회한다 (1-based)")
        void getRank() {
            redisTemplate.opsForZSet().add("ranking:all:20260406", "1", 100.0);
            redisTemplate.opsForZSet().add("ranking:all:20260406", "2", 200.0);

            Long rank = rankingRedisRepository.getRank("20260406", 2L);

            assertThat(rank).isEqualTo(1L);
        }

        @Test
        @DisplayName("랭킹에 없는 상품은 null을 반환한다")
        void rankNotFound() {
            Long rank = rankingRedisRepository.getRank("20260406", 999L);

            assertThat(rank).isNull();
        }
    }

    @Nested
    @DisplayName("점수 조회")
    class GetScore {

        @Test
        @DisplayName("특정 상품의 점수를 조회한다")
        void getScore() {
            redisTemplate.opsForZSet().add("ranking:all:20260406", "1", 150.5);

            Double score = rankingRedisRepository.getScore("20260406", 1L);

            assertThat(score).isEqualTo(150.5);
        }

        @Test
        @DisplayName("랭킹에 없는 상품은 null을 반환한다")
        void scoreNotFound() {
            Double score = rankingRedisRepository.getScore("20260406", 999L);

            assertThat(score).isNull();
        }
    }
}
