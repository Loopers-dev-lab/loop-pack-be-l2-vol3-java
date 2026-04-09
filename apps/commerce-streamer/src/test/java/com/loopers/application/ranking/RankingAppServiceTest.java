package com.loopers.application.ranking;

import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.assertj.core.data.Offset;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RankingAppService 통합 테스트")
class RankingAppServiceTest {

    @Autowired
    private RankingAppService rankingAppService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("조회 이벤트 시 hourly 점수가 반영된다")
    void updateViewRanking() {
        Long productId = 101L;

        rankingAppService.updateViewRanking(productId);

        Double hourlyScore = redisTemplate.opsForZSet().score(currentHourlyKey(), "101");
        assertThat(hourlyScore).isEqualTo(0.1);
    }

    @Test
    @DisplayName("좋아요 이벤트 시 hourly 점수가 반영된다")
    void updateLikeRanking() {
        Long productId = 101L;

        rankingAppService.updateLikeRanking(productId);

        Double hourlyScore = redisTemplate.opsForZSet().score(currentHourlyKey(), "101");
        assertThat(hourlyScore).isEqualTo(0.2);
    }

    @Test
    @DisplayName("주문 이벤트 시 각 상품의 hourly 점수가 반영된다")
    void updateOrderRanking() {
        List<Long> productIds = List.of(101L, 202L);

        rankingAppService.updateOrderRanking(productIds, 20000);

        Double hourly101 = redisTemplate.opsForZSet().score(currentHourlyKey(), "101");
        Double hourly202 = redisTemplate.opsForZSet().score(currentHourlyKey(), "202");
        assertThat(hourly101).isEqualTo(Math.log1p(10000) * 0.6);
        assertThat(hourly202).isEqualTo(Math.log1p(10000) * 0.6);
    }

    @Test
    @DisplayName("주문 금액이 클수록 hourly 점수가 더 크게 반영된다")
    void updateOrderRanking_usesTotalAmount() {
        rankingAppService.updateOrderRanking(List.of(101L), 10_000);
        rankingAppService.updateOrderRanking(List.of(202L), 1_000_000);

        Double hourly101 = redisTemplate.opsForZSet().score(currentHourlyKey(), "101");
        Double hourly202 = redisTemplate.opsForZSet().score(currentHourlyKey(), "202");

        assertThat(hourly101).isNotNull();
        assertThat(hourly202).isNotNull();
        assertThat(hourly202).isGreaterThan(hourly101);
    }

    @Test
    @DisplayName("같은 상품에 여러 이벤트가 누적되면 hourly 점수가 합산된다")
    void accumulateHourlyScores() {
        Long productId = 101L;

        rankingAppService.updateViewRanking(productId);
        rankingAppService.updateViewRanking(productId);
        rankingAppService.updateLikeRanking(productId);

        // hourly 점수 합산: 0.1+0.1+0.2 = 0.4
        Double hourlyScore = redisTemplate.opsForZSet().score(currentHourlyKey(), "101");
        assertThat(hourlyScore).isCloseTo(0.4, Offset.offset(0.001));
    }

    @Test
    @DisplayName("hourly 키에 TTL이 설정된다")
    void hourlyKeyTtlIsSet() {
        rankingAppService.updateViewRanking(101L);

        Long ttl = redisTemplate.getExpire(currentHourlyKey());
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(7200L);
    }

    private String currentHourlyKey() {
        return "ranking:hourly:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    }
}
