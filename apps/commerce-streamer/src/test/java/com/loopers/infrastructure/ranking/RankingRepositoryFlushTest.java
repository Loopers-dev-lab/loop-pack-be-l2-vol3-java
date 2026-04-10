package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingRepositoryFlushTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private RankingRepository rankingRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private String rankingKey(LocalDate date) {
        return "ranking:all:" + date.format(DATE_FORMAT);
    }

    @DisplayName("flush() 호출 시 각 상품의 delta가 Redis ZSET에 반영된다.")
    @Test
    void flushWritesScoresToRedis() {
        // arrange
        LocalDate date = LocalDate.now();
        Map<LocalDate, Map<Long, Double>> delta = Map.of(
            date, Map.of(1L, 0.4, 2L, 0.6)
        );

        // act
        rankingRepository.flush(delta);

        // assert
        String key = rankingKey(date);
        assertThat(redisTemplate.opsForZSet().score(key, "1")).isEqualTo(0.4);
        assertThat(redisTemplate.opsForZSet().score(key, "2")).isEqualTo(0.6);
    }

    @DisplayName("flush() 호출 시 같은 상품에 대해 누적 합산된다.")
    @Test
    void flushAccumulatesExistingScore() {
        // arrange
        LocalDate date = LocalDate.now();
        Map<Long, Double> firstDelta = new HashMap<>();
        firstDelta.put(1L, 0.2);
        Map<Long, Double> secondDelta = new HashMap<>();
        secondDelta.put(1L, 0.3);

        // act
        rankingRepository.flush(Map.of(date, firstDelta));
        rankingRepository.flush(Map.of(date, secondDelta));

        // assert
        Double score = redisTemplate.opsForZSet().score(rankingKey(date), "1");
        assertThat(score).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.0001));
    }

    @DisplayName("flush() 호출 시 TTL이 2일로 설정된다.")
    @Test
    void flushSetsTwoDayTtl() {
        // arrange
        LocalDate date = LocalDate.now();
        Map<LocalDate, Map<Long, Double>> delta = Map.of(date, Map.of(1L, 0.1));

        // act
        rankingRepository.flush(delta);

        // assert
        Long ttl = redisTemplate.getExpire(rankingKey(date));
        assertThat(ttl).isNotNull().isPositive().isLessThanOrEqualTo(2 * 24 * 60 * 60L);
    }

    @DisplayName("flush() 호출 시 날짜별 다른 key에 반영된다.")
    @Test
    void flushWritesToCorrectDateKeys() {
        // arrange
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Map<LocalDate, Map<Long, Double>> delta = new HashMap<>();
        delta.put(today, Map.of(1L, 0.1));
        delta.put(yesterday, Map.of(1L, 0.2));

        // act
        rankingRepository.flush(delta);

        // assert
        assertThat(redisTemplate.opsForZSet().score(rankingKey(today), "1")).isEqualTo(0.1);
        assertThat(redisTemplate.opsForZSet().score(rankingKey(yesterday), "1")).isEqualTo(0.2);
    }

    @DisplayName("flush() 에 빈 map이 전달되면 아무 것도 하지 않는다.")
    @Test
    void flushWithEmptyMapDoesNothing() {
        // act & assert (예외 없이 정상 종료)
        rankingRepository.flush(Map.of());
    }
}
