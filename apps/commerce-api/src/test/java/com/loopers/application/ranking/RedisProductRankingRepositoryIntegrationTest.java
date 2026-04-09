package com.loopers.application.ranking;

import com.loopers.config.redis.RedisConfig;
import com.loopers.infrastructure.ranking.redis.RedisProductRankingRepository;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(RedisTestContainersConfig.class)
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "docker.tests", matches = "true")
class RedisProductRankingRepositoryIntegrationTest {

    @Autowired
    private RedisProductRankingRepository redisProductRankingRepository;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("실제 Redis ZSET에서 상위 랭킹과 개별 순위를 조회한다")
    void readRankingFromRedis() {
        LocalDate metricDate = LocalDate.of(2025, 9, 7);
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        String rankingKey = redisProductRankingRepository.buildDailyRankingKey(metricDate);
        redisTemplate.opsForZSet().add(rankingKey, firstProductId.toString(), 3.5d);
        redisTemplate.opsForZSet().add(rankingKey, secondProductId.toString(), 1.2d);

        List<RankingProductView> topRankings = redisProductRankingRepository.findTop(metricDate, 2);
        RankingProductView productRank = redisProductRankingRepository.findProductRank(metricDate, secondProductId);

        assertThat(topRankings).hasSize(2);
        assertThat(topRankings.get(0).productId()).isEqualTo(firstProductId);
        assertThat(topRankings.get(0).rank()).isEqualTo(1L);
        assertThat(topRankings.get(0).score()).isEqualTo(3.5d);
        assertThat(productRank.productId()).isEqualTo(secondProductId);
        assertThat(productRank.rank()).isEqualTo(2L);
        assertThat(productRank.score()).isEqualTo(1.2d);
    }
}
