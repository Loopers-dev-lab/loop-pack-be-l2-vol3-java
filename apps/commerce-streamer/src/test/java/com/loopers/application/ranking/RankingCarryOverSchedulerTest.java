package com.loopers.application.ranking;

import com.loopers.domain.ranking.ScoreFormula;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.loopers.application.ranking.RankingScoreUpdater.*;
import static org.assertj.core.api.Assertions.assertThat;
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

    private static final int CARRY_OVER_CAP = 10_000;
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 10);
    private static final String TODAY_KEY = "ranking:all:20260410";
    private static final String TOMORROW_KEY = "ranking:all:20260411";

    @BeforeEach
    void setUp() {
        RankingProperties properties = new RankingProperties(
            new ScoreFormula.Weights(0.1, 0.2, 0.7), 0.1, 0.97, CARRY_OVER_CAP,
            Map.of(), 0, null
        );
        scheduler = new RankingCarryOverScheduler(writeTemplate, properties);
    }

    private void stubZSetOps() {
        stubZSetOps(100L);
    }

    private void stubZSetOps(long zCardReturn) {
        when(writeTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.unionAndStore(anyString(), anyCollection(), anyString(), any(), any()))
            .thenReturn(10L);
        lenient().when(zSetOps.unionAndStore(anyString(), anyList(), anyString(), any(), any()))
            .thenReturn(10L);
        when(writeTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        when(zSetOps.zCard(anyString())).thenReturn(zCardReturn);
    }

    @Nested
    @DisplayName("일간 carry-over")
    class DailyCarryOver {

        @Test
        @DisplayName("ZUNIONSTORE로 오늘 score × 0.1을 내일 키에 복사")
        void callsUnionAndStoreWithCorrectParams() {
            stubZSetOps();

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
        @DisplayName("내일 키에 ZSET TTL(691200초 = 8일) 설정")
        void setsTtlOnTomorrowKey() {
            stubZSetOps();

            scheduler.carryOver(TODAY);

            verify(writeTemplate).expire(TOMORROW_KEY, RANKING_ZSET_TTL_SECONDS, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("오늘 키와 내일 키가 하루 차이")
        void keysDifferByOneDay() {
            stubZSetOps();

            scheduler.carryOver(LocalDate.of(2026, 12, 31));

            verify(zSetOps).unionAndStore(
                eq("ranking:all:20261231"),
                eq(Collections.emptyList()),
                eq("ranking:all:20270101"),
                any(), any()
            );
        }

        @Test
        @DisplayName("Redis 장애 시 예외를 삼키고 로그만 남김")
        void onFailure_doesNotThrow() {
            when(writeTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis 연결 실패"));

            assertThatCode(() -> scheduler.carryOver(TODAY)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("carry-over 후 Trim (ZSET 크기 관리)")
    class ZsetTrim {

        @Test
        @DisplayName("daily carry-over 후 ZSET 크기가 cap 초과 시 하위 score 제거")
        void dailyTrim_whenExceedsCap() {
            long oversized = 15_000L;
            stubZSetOps(oversized);

            scheduler.carryOver(TODAY);

            verify(zSetOps).removeRange(TOMORROW_KEY, 0, oversized - CARRY_OVER_CAP - 1);
        }

        @Test
        @DisplayName("daily carry-over 후 ZSET 크기가 cap 이하면 trim 미실행")
        void dailyTrim_whenWithinCap() {
            stubZSetOps(5_000L);

            scheduler.carryOver(TODAY);

            verify(zSetOps, never()).removeRange(anyString(), anyLong(), anyLong());
        }

        @Test
        @DisplayName("실험 활성화 시 variant carry-over에도 trim 적용")
        void experimentVariant_trimApplied() {
            RankingProperties experimentProps = new RankingProperties(
                new ScoreFormula.Weights(0.1, 0.2, 0.7), 0.1, 0.97, CARRY_OVER_CAP,
                Map.of(), 0,
                new RankingProperties.Experiment(true, Map.of(
                    "A", new RankingProperties.Variant(
                        new ScoreFormula.Weights(0.1, 0.2, 0.7), "ranking:exp:A:"),
                    "B", new RankingProperties.Variant(
                        new ScoreFormula.Weights(0.2, 0.3, 0.5), "ranking:exp:B:")
                ))
            );
            RankingCarryOverScheduler expScheduler = new RankingCarryOverScheduler(writeTemplate, experimentProps);

            long oversized = 12_000L;
            stubZSetOps(oversized);

            expScheduler.carryOver(TODAY);

            // variant A, B 두 키 모두 trim 호출
            verify(zSetOps).removeRange("ranking:exp:A:20260411", 0, oversized - CARRY_OVER_CAP - 1);
            verify(zSetOps).removeRange("ranking:exp:B:20260411", 0, oversized - CARRY_OVER_CAP - 1);
        }
    }
}
