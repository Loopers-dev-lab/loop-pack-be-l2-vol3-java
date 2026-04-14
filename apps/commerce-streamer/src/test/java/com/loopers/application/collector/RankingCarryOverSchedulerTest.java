package com.loopers.application.collector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RankingCarryOverSchedulerTest {

    private RankingCarryOverScheduler scheduler;
    private RankingKeyGenerator rankingKeyGenerator;
    private RedisTemplate<String, String> redisTemplate;
    private ZSetOperations<String, String> zSetOperations;

    @BeforeEach
    void setUp() {
        rankingKeyGenerator = new RankingKeyGenerator("Asia/Seoul");
        redisTemplate = mock(RedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        scheduler = new RankingCarryOverScheduler(rankingKeyGenerator, redisTemplate);
        ReflectionTestUtils.setField(scheduler, "ttlDays", 2L);
        ReflectionTestUtils.setField(scheduler, "carryOverEnabled", true);
        ReflectionTestUtils.setField(scheduler, "carryOverTopSize", 3L);
        ReflectionTestUtils.setField(scheduler, "carryOverFactor", 0.3);
    }

    @DisplayName("23:50 carry-over 시 오늘 top score를 내일 키에 factor 비율로 복사한다")
    @Test
    void carryOverTopRanksToTomorrow() {
        LocalDate today = rankingKeyGenerator.today();
        String sourceKey = rankingKeyGenerator.dailyKey(today);
        String targetKey = rankingKeyGenerator.dailyKey(today.plusDays(1));

        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(ZSetOperations.TypedTuple.of("10", 100.0));
        tuples.add(ZSetOperations.TypedTuple.of("20", 50.0));
        when(zSetOperations.reverseRangeWithScores(eq(sourceKey), eq(0L), eq(2L))).thenReturn(tuples);

        scheduler.carryOverTopRanksToTomorrow();

        verify(zSetOperations).add(targetKey, "10", 30.0);
        verify(zSetOperations).add(targetKey, "20", 15.0);
        verify(redisTemplate).expire(targetKey, Duration.ofDays(2));
    }

    @DisplayName("carry-over가 비활성화되면 복사하지 않는다")
    @Test
    void skipWhenDisabled() {
        ReflectionTestUtils.setField(scheduler, "carryOverEnabled", false);
        scheduler.carryOverTopRanksToTomorrow();

        verify(zSetOperations, never()).reverseRangeWithScores(anyString(), anyLong(), anyLong());
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }
}
