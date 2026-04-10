package com.loopers.application.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingCarryOverSchedulerTest {

    @Mock
    private RedisTemplate<String, String> writeTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private RankingCarryOverScheduler scheduler;

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 10);
    private static final String TODAY_KEY = "ranking:all:20260410";
    private static final String TOMORROW_KEY = "ranking:all:20260411";

    @BeforeEach
    void setUp() {
        RankingProperties properties = new RankingProperties(
            new RankingProperties.Weights(0.1, 0.2, 0.7), 0.1
        );
        scheduler = new RankingCarryOverScheduler(writeTemplate, properties);
    }

    @Test
    @DisplayName("ZUNIONSTORE로 오늘 score × 0.1을 내일 키에 복사")
    void carryOver_callsUnionAndStoreWithCorrectParams() {
        when(writeTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.unionAndStore(anyString(), anyCollection(), anyString(), any(), any()))
            .thenReturn(10L);
        when(writeTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        when(zSetOps.zCard(anyString())).thenReturn(10L);

        scheduler.carryOver(TODAY);

        verify(zSetOps).unionAndStore(
            eq(TODAY_KEY),
            eq(Collections.emptyList()),
            eq(TOMORROW_KEY),
            eq(Aggregate.SUM),
            eq(Weights.of(0.1))
        );
    }

    @Test
    @DisplayName("내일 키에 TTL 172800초 설정")
    void carryOver_setsTtlOnTomorrowKey() {
        when(writeTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.unionAndStore(anyString(), anyCollection(), anyString(), any(), any()))
            .thenReturn(10L);
        when(writeTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        when(zSetOps.zCard(anyString())).thenReturn(10L);

        scheduler.carryOver(TODAY);

        verify(writeTemplate).expire(TOMORROW_KEY, 172_800L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Redis 장애 시 예외를 삼키고 로그만 남김")
    void carryOver_onFailure_doesNotThrow() {
        when(writeTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis 연결 실패"));

        assertThatCode(() -> scheduler.carryOver(TODAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("오늘 키와 내일 키가 하루 차이")
    void carryOver_keysDifferByOneDay() {
        when(writeTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.unionAndStore(anyString(), anyCollection(), anyString(), any(), any()))
            .thenReturn(5L);
        when(writeTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        when(zSetOps.zCard(anyString())).thenReturn(5L);

        scheduler.carryOver(LocalDate.of(2026, 12, 31));

        verify(zSetOps).unionAndStore(
            eq("ranking:all:20261231"),
            eq(Collections.emptyList()),
            eq("ranking:all:20270101"),
            any(), any()
        );
    }
}
