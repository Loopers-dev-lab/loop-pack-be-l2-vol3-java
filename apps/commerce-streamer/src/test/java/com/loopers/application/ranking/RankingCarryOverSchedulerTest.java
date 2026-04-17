package com.loopers.application.ranking;

import com.loopers.domain.ranking.ScoreFormula;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
    @DisplayName("주간 랭킹 (buildWeeklyRanking)")
    class WeeklyRanking {

        @Test
        @DisplayName("최근 7일 daily ZSET을 동일 가중치로 합산하여 내일자 weekly ZSET 생성")
        void buildsWeeklyFromSevenDays() {
            stubZSetOps();
            LocalDate tomorrow = TODAY.plusDays(1);

            scheduler.buildWeeklyRanking(TODAY, tomorrow);

            verify(zSetOps).unionAndStore(
                eq(TODAY_KEY),
                argThat((Collection<String> keys) -> keys.size() == 6),
                eq("ranking:weekly:20260411"),
                eq(Aggregate.SUM),
                eq(Weights.of(1, 1, 1, 1, 1, 1, 1))
            );
        }

        @Test
        @DisplayName("weekly ZSET에 AGGREGATED TTL(172800초 = 2일) 설정")
        void setsAggregatedTtl() {
            stubZSetOps();
            LocalDate tomorrow = TODAY.plusDays(1);

            scheduler.buildWeeklyRanking(TODAY, tomorrow);

            verify(writeTemplate).expire("ranking:weekly:20260411",
                RANKING_AGGREGATED_TTL_SECONDS, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("7일 daily 키가 오늘부터 6일 전까지 정확히 생성됨")
        @SuppressWarnings("unchecked")
        void dailyKeysSpanSevenDays() {
            stubZSetOps();
            LocalDate tomorrow = TODAY.plusDays(1);

            scheduler.buildWeeklyRanking(TODAY, tomorrow);

            ArgumentCaptor<String> firstKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Collection<String>> otherKeysCaptor = ArgumentCaptor.forClass(Collection.class);
            verify(zSetOps).unionAndStore(
                firstKeyCaptor.capture(),
                otherKeysCaptor.capture(),
                anyString(), any(), any()
            );

            List<String> allKeys = new java.util.ArrayList<>();
            allKeys.add(firstKeyCaptor.getValue());
            allKeys.addAll(otherKeysCaptor.getValue());

            assertThat(allKeys).containsExactly(
                "ranking:all:20260410",
                "ranking:all:20260409",
                "ranking:all:20260408",
                "ranking:all:20260407",
                "ranking:all:20260406",
                "ranking:all:20260405",
                "ranking:all:20260404"
            );
        }

        @Test
        @DisplayName("Redis 장애 시 예외를 삼키고 로그만 남김")
        void onFailure_doesNotThrow() {
            when(writeTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis 연결 실패"));

            assertThatCode(() -> scheduler.buildWeeklyRanking(TODAY, TODAY.plusDays(1)))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("월간 랭킹 (buildMonthlyRanking)")
    class MonthlyRanking {

        @Test
        @DisplayName("오늘 monthly × 0.97 + 오늘 daily × 1.0 → 내일 monthly")
        void buildsMonthlyWithDecay() {
            stubZSetOps();
            LocalDate tomorrow = TODAY.plusDays(1);

            scheduler.buildMonthlyRanking(TODAY, tomorrow);

            verify(zSetOps).unionAndStore(
                eq("ranking:monthly:20260410"),
                eq(Collections.singletonList(TODAY_KEY)),
                eq("ranking:monthly:20260411"),
                eq(Aggregate.SUM),
                eq(Weights.of(0.97, 1.0))
            );
        }

        @Test
        @DisplayName("monthly ZSET에 AGGREGATED TTL(172800초 = 2일) 설정")
        void setsAggregatedTtl() {
            stubZSetOps();
            LocalDate tomorrow = TODAY.plusDays(1);

            scheduler.buildMonthlyRanking(TODAY, tomorrow);

            verify(writeTemplate).expire("ranking:monthly:20260411",
                RANKING_AGGREGATED_TTL_SECONDS, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("Redis 장애 시 예외를 삼키고 로그만 남김")
        void onFailure_doesNotThrow() {
            when(writeTemplate.opsForZSet()).thenThrow(new RuntimeException("Redis 연결 실패"));

            assertThatCode(() -> scheduler.buildMonthlyRanking(TODAY, TODAY.plusDays(1)))
                .doesNotThrowAnyException();
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
        @DisplayName("monthly carry-over 후 ZSET 크기가 cap 초과 시 하위 score 제거")
        void monthlyTrim_whenExceedsCap() {
            long oversized = 20_000L;
            stubZSetOps(oversized);

            scheduler.buildMonthlyRanking(TODAY, TODAY.plusDays(1));

            verify(zSetOps).removeRange("ranking:monthly:20260411", 0, oversized - CARRY_OVER_CAP - 1);
        }

        @Test
        @DisplayName("weekly 랭킹에는 trim이 적용되지 않음 — 합산 재생성이므로 누적 없음")
        void weeklyTrim_neverApplied() {
            stubZSetOps(50_000L);

            scheduler.buildWeeklyRanking(TODAY, TODAY.plusDays(1));

            verify(zSetOps, never()).removeRange(eq("ranking:weekly:20260411"), anyLong(), anyLong());
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
