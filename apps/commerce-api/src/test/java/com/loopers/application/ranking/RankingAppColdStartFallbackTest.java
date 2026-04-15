package com.loopers.application.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.ranking.RankingKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingApp 콜드스타트 fallback")
class RankingAppColdStartFallbackTest {

    private RankingRepository rankingRepository;
    private MvProductRankRepository mvProductRankRepository;
    private RankingProductCache productCache;
    private RankingApp rankingApp;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        mvProductRankRepository = mock(MvProductRankRepository.class);
        productCache = mock(RankingProductCache.class);
        rankingApp = new RankingApp(rankingRepository, mvProductRankRepository, productCache);
    }

    @DisplayName("flag off + 현재 periodKey 비어있음 → 빈 페이지 반환 (fallback 미발생)")
    @Test
    void fallback_disabled_returnsEmpty() {
        ReflectionTestUtils.setField(rankingApp, "coldStartFallbackEnabled", false);
        LocalDate date = LocalDate.of(2026, 4, 13);
        String currentKey = RankingKeyGenerator.weeklyPeriodKey(date);
        when(mvProductRankRepository.findByPeriodKey(eq(RankPeriodType.WEEKLY), eq(currentKey), anyLong(), anyLong()))
                .thenReturn(List.of());
        when(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, currentKey)).thenReturn(0L);
        when(mvProductRankRepository.findLastUpdatedAt(RankPeriodType.WEEKLY, currentKey)).thenReturn(Optional.empty());

        RankingPageResult result = rankingApp.getTopN(RankingPeriod.WEEKLY, date, 0, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.isFallback()).isFalse();
        assertThat(result.periodKey()).isEqualTo(currentKey);
    }

    @DisplayName("flag on + 현재 periodKey 비어있음 → 직전 periodKey로 fallback")
    @Test
    void fallback_enabled_usesPrevious() {
        ReflectionTestUtils.setField(rankingApp, "coldStartFallbackEnabled", true);
        LocalDate date = LocalDate.of(2026, 4, 13);
        String currentKey = RankingKeyGenerator.weeklyPeriodKey(date);
        String previousKey = RankingKeyGenerator.previousWeeklyPeriodKey(date);

        when(mvProductRankRepository.findByPeriodKey(eq(RankPeriodType.WEEKLY), eq(currentKey), anyLong(), anyLong()))
                .thenReturn(List.of());
        when(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, currentKey)).thenReturn(0L);
        when(mvProductRankRepository.findLastUpdatedAt(RankPeriodType.WEEKLY, currentKey)).thenReturn(Optional.empty());

        when(mvProductRankRepository.findByPeriodKey(eq(RankPeriodType.WEEKLY), eq(previousKey), anyLong(), anyLong()))
                .thenReturn(List.of(new RankingEntry(1L, 100.0)));
        when(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, previousKey)).thenReturn(1L);
        when(mvProductRankRepository.findLastUpdatedAt(RankPeriodType.WEEKLY, previousKey)).thenReturn(Optional.empty());
        when(productCache.findAllByIds(List.of(1L))).thenReturn(java.util.Map.of());

        RankingPageResult result = rankingApp.getTopN(RankingPeriod.WEEKLY, date, 0, 20);

        assertThat(result.isFallback()).isTrue();
        assertThat(result.periodKey()).isEqualTo(previousKey);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(mvProductRankRepository).findByPeriodKey(RankPeriodType.WEEKLY, previousKey, 0L, 20L);
    }

    @DisplayName("flag on + 현재 periodKey 데이터 존재 → fallback 미발생")
    @Test
    void fallback_enabled_butCurrentHasData_noFallback() {
        ReflectionTestUtils.setField(rankingApp, "coldStartFallbackEnabled", true);
        LocalDate date = LocalDate.of(2026, 4, 13);
        String currentKey = RankingKeyGenerator.weeklyPeriodKey(date);

        when(mvProductRankRepository.findByPeriodKey(eq(RankPeriodType.WEEKLY), eq(currentKey), anyLong(), anyLong()))
                .thenReturn(List.of(new RankingEntry(1L, 100.0)));
        when(mvProductRankRepository.countByPeriodKey(RankPeriodType.WEEKLY, currentKey)).thenReturn(1L);
        when(mvProductRankRepository.findLastUpdatedAt(RankPeriodType.WEEKLY, currentKey)).thenReturn(Optional.empty());
        when(productCache.findAllByIds(List.of(1L))).thenReturn(java.util.Map.of());

        RankingPageResult result = rankingApp.getTopN(RankingPeriod.WEEKLY, date, 0, 20);

        assertThat(result.isFallback()).isFalse();
        assertThat(result.periodKey()).isEqualTo(currentKey);
    }
}
