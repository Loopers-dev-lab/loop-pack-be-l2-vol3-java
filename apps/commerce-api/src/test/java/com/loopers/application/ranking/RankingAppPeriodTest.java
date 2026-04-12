package com.loopers.application.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.RankPeriodType;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingApp period 분기 단위 테스트")
class RankingAppPeriodTest {

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

    @DisplayName("period=DAILY → RankingRepository(Redis) 호출")
    @Test
    void daily_usesRankingRepository() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        when(rankingRepository.findTopN(date, 0, 20)).thenReturn(List.of());
        when(rankingRepository.countMembers(date)).thenReturn(0L);

        // act
        rankingApp.getTopN(RankingPeriod.DAILY, date, 0, 20);

        // assert
        verify(rankingRepository).findTopN(date, 0, 20);
        verify(mvProductRankRepository, never()).findByPeriodKey(any(), anyString(), anyLong(), anyLong());
    }

    @DisplayName("period=WEEKLY → MvProductRankRepository 호출")
    @Test
    void weekly_usesMvRepository() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        when(mvProductRankRepository.findByPeriodKey(eq(RankPeriodType.WEEKLY), anyString(), eq(0L), eq(20L)))
                .thenReturn(List.of());
        when(mvProductRankRepository.countByPeriodKey(eq(RankPeriodType.WEEKLY), anyString())).thenReturn(0L);

        // act
        rankingApp.getTopN(RankingPeriod.WEEKLY, date, 0, 20);

        // assert
        verify(mvProductRankRepository).findByPeriodKey(eq(RankPeriodType.WEEKLY), anyString(), eq(0L), eq(20L));
        verify(rankingRepository, never()).findTopN(any(), anyLong(), anyLong());
    }

    @DisplayName("period=MONTHLY → MvProductRankRepository(MONTHLY) 호출")
    @Test
    void monthly_usesMvRepository() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        when(mvProductRankRepository.findByPeriodKey(eq(RankPeriodType.MONTHLY), anyString(), eq(0L), eq(20L)))
                .thenReturn(List.of());
        when(mvProductRankRepository.countByPeriodKey(eq(RankPeriodType.MONTHLY), anyString())).thenReturn(0L);

        // act
        rankingApp.getTopN(RankingPeriod.MONTHLY, date, 0, 20);

        // assert
        verify(mvProductRankRepository).findByPeriodKey(eq(RankPeriodType.MONTHLY), anyString(), eq(0L), eq(20L));
    }

    @DisplayName("period 없는 기존 메서드는 DAILY로 동작한다")
    @Test
    void legacyMethod_defaultsToDaily() {
        // arrange
        LocalDate date = LocalDate.of(2026, 4, 11);
        when(rankingRepository.findTopN(date, 0, 20)).thenReturn(List.of());
        when(rankingRepository.countMembers(date)).thenReturn(0L);

        // act
        rankingApp.getTopN(date, 0, 20);

        // assert
        verify(rankingRepository).findTopN(date, 0, 20);
    }
}
