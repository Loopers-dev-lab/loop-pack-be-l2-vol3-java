package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.mv.MvRankEntry;
import com.loopers.domain.ranking.mv.MvRankingQueryRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Redis miss 시 LAST_7D / LAST_30D 는 MV 테이블로 fallback 한다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingServiceMvFallbackTest {

    private final RankingRedisRepository redisRepository = Mockito.mock(RankingRedisRepository.class);
    private final RankingFallbackAggregator bucketFallback = Mockito.mock(RankingFallbackAggregator.class);
    private final MvRankingQueryRepository mvRepository = Mockito.mock(MvRankingQueryRepository.class);
    private final ExperimentGroupResolver groupResolver = Mockito.mock(ExperimentGroupResolver.class);
    private final RankingKeyResolver keyResolver = new RankingKeyResolver(Clock.system(ZoneId.of("Asia/Seoul")));

    private final RankingService service = new RankingService(
            redisRepository, keyResolver, bucketFallback, mvRepository, groupResolver);

    @Test
    void Redis_miss_시_LAST_7D_는_MV_테이블에서_어제_anchor_로_조회한다() {
        // given: Redis 빈 응답
        when(redisRepository.getRankings(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(mvRepository.findLast7d(eq(LocalDate.of(2026, 4, 14)), eq("control"), eq(0), eq(20)))
                .thenReturn(List.of(
                        new MvRankEntry(1L, 99.9, 1),
                        new MvRankEntry(2L, 88.8, 2)
                ));

        // when: 조회 기준일 2026-04-15 → anchor_date = 2026-04-14
        List<RankEntry> result = service.getRankEntries(
                RankingPeriod.LAST_7D, LocalDate.of(2026, 4, 15), 0, 20, "control");

        // then: MV 결과를 rank_position 순으로 투영
        assertThat(result).extracting(RankEntry::productId).containsExactly(1L, 2L);
        assertThat(result).extracting(RankEntry::rank).containsExactly(1, 2);
        assertThat(result).extracting(RankEntry::score).containsExactly(99.9, 88.8);
        Mockito.verify(bucketFallback, Mockito.never()).aggregate(any(), any());
    }

    @Test
    void LAST_30D_도_동일한_MV_fallback_경로를_탄다() {
        when(redisRepository.getRankings(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(mvRepository.findLast30d(eq(LocalDate.of(2026, 4, 14)), eq("control"), eq(0), eq(10)))
                .thenReturn(List.of(new MvRankEntry(5L, 55.5, 1)));

        List<RankEntry> result = service.getRankEntries(
                RankingPeriod.LAST_30D, LocalDate.of(2026, 4, 15), 0, 10, "control");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(5L);
    }

    @Test
    void Redis_가_응답하면_MV_는_호출되지_않는다() {
        when(redisRepository.getRankings(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new RankEntry(7L, 42.0, 1)));

        List<RankEntry> result = service.getRankEntries(
                RankingPeriod.LAST_7D, LocalDate.of(2026, 4, 15), 0, 20, "control");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(7L);
        Mockito.verifyNoInteractions(mvRepository);
    }

    @Test
    void 현재_anchor_MV_가_비어있으면_전일_anchor_로_자동_fallback_한다() {
        when(redisRepository.getRankings(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        // 오늘 anchor (4/14) 비어있음 → 전일 (4/13) 에 데이터 있음
        when(mvRepository.findLast7d(eq(LocalDate.of(2026, 4, 14)), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(mvRepository.findLast7d(eq(LocalDate.of(2026, 4, 13)), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(new MvRankEntry(1L, 50.0, 1)));

        List<RankEntry> result = service.getRankEntries(
                RankingPeriod.LAST_7D, LocalDate.of(2026, 4, 15), 0, 20, "control");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(1L);
    }

    @Test
    void 전일_fallback_도_3일간_비어있으면_빈_리스트를_반환한다() {
        when(redisRepository.getRankings(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(mvRepository.findLast7d(any(), anyString(), anyInt(), anyInt())).thenReturn(List.of());

        List<RankEntry> result = service.getRankEntries(
                RankingPeriod.LAST_7D, LocalDate.of(2026, 4, 15), 0, 20, "control");

        assertThat(result).isEmpty();
    }

    @Test
    void totalCount_도_Redis_miss_시_MV_카운트로_fallback_한다() {
        when(redisRepository.getTotalCount(anyString())).thenReturn(0L);
        when(mvRepository.countLast7d(eq(LocalDate.of(2026, 4, 14)), eq("control"))).thenReturn(100L);

        long total = service.getTotalCount(RankingPeriod.LAST_7D, LocalDate.of(2026, 4, 15), "control");

        assertThat(total).isEqualTo(100L);
    }
}
