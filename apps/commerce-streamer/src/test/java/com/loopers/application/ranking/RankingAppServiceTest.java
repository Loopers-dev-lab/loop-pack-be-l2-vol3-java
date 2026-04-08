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

import java.time.LocalDate;
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

    private final String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    private final String rawKey = "ranking:raw:" + date;
    private final String hourlyKey = "ranking:hourly:" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("조회 이벤트 시 raw 카운트와 hourly 점수가 반영된다")
    void updateViewRanking() {
        Long productId = 101L;

        rankingAppService.updateViewRanking(productId);

        // raw count
        Object views = redisTemplate.opsForHash().get(rawKey, "101:v");
        assertThat(views).isNotNull();
        assertThat(Long.parseLong(views.toString())).isEqualTo(1);

        // hourly score
        Double hourlyScore = redisTemplate.opsForZSet().score(hourlyKey, "101");
        assertThat(hourlyScore).isEqualTo(0.1);
    }

    @Test
    @DisplayName("좋아요 이벤트 시 raw 카운트와 hourly 점수가 반영된다")
    void updateLikeRanking() {
        Long productId = 101L;

        rankingAppService.updateLikeRanking(productId);

        Object likes = redisTemplate.opsForHash().get(rawKey, "101:l");
        assertThat(likes).isNotNull();
        assertThat(Long.parseLong(likes.toString())).isEqualTo(1);

        Double hourlyScore = redisTemplate.opsForZSet().score(hourlyKey, "101");
        assertThat(hourlyScore).isEqualTo(0.2);
    }

    @Test
    @DisplayName("주문 이벤트 시 각 상품의 raw 금액과 hourly 점수가 반영된다")
    void updateOrderRanking() {
        List<Long> productIds = List.of(101L, 202L);

        rankingAppService.updateOrderRanking(productIds, 20000);

        // 금액이 상품별로 균등 분배: 20000 / 2 = 10000
        Object orders101 = redisTemplate.opsForHash().get(rawKey, "101:o");
        Object orders202 = redisTemplate.opsForHash().get(rawKey, "202:o");
        assertThat(Long.parseLong(orders101.toString())).isEqualTo(10000);
        assertThat(Long.parseLong(orders202.toString())).isEqualTo(10000);

        Double hourly101 = redisTemplate.opsForZSet().score(hourlyKey, "101");
        Double hourly202 = redisTemplate.opsForZSet().score(hourlyKey, "202");
        assertThat(hourly101).isEqualTo(0.6);
        assertThat(hourly202).isEqualTo(0.6);
    }

    @Test
    @DisplayName("같은 상품에 여러 이벤트가 누적되면 raw 카운트가 합산된다")
    void accumulateRawCounts() {
        Long productId = 101L;

        rankingAppService.updateViewRanking(productId);
        rankingAppService.updateViewRanking(productId);
        rankingAppService.updateLikeRanking(productId);

        Object views = redisTemplate.opsForHash().get(rawKey, "101:v");
        Object likes = redisTemplate.opsForHash().get(rawKey, "101:l");
        assertThat(Long.parseLong(views.toString())).isEqualTo(2);
        assertThat(Long.parseLong(likes.toString())).isEqualTo(1);

        // hourly 점수도 합산: 0.1+0.1+0.2 = 0.4
        Double hourlyScore = redisTemplate.opsForZSet().score(hourlyKey, "101");
        assertThat(hourlyScore).isCloseTo(0.4, Offset.offset(0.001));
    }

    @Test
    @DisplayName("raw 키에 TTL이 설정된다")
    void rawKeyTtlIsSet() {
        rankingAppService.updateViewRanking(101L);

        Long ttl = redisTemplate.getExpire(rawKey);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(172800L);
    }

    @Test
    @DisplayName("hourly 키에 TTL이 설정된다")
    void hourlyKeyTtlIsSet() {
        rankingAppService.updateViewRanking(101L);

        Long ttl = redisTemplate.getExpire(hourlyKey);
        assertThat(ttl).isGreaterThan(0);
        assertThat(ttl).isLessThanOrEqualTo(7200L);
    }

    @Test
    @DisplayName("이벤트 발생 시 마지막 이벤트 시각이 기록된다")
    void lastEventTimestampTracked() {
        rankingAppService.updateViewRanking(101L);

        Object lastEvent = redisTemplate.opsForHash().get(rawKey, "101:t");
        assertThat(lastEvent).isNotNull();
        long timestamp = Long.parseLong(lastEvent.toString());
        assertThat(timestamp).isGreaterThan(0);
    }
}
