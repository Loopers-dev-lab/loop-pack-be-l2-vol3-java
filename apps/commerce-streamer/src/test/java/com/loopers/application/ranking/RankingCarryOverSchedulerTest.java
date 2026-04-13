package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.mockito.Mockito.*;

class RankingCarryOverSchedulerTest {

    private ProductRankingRepository productRankingRepository;
    private RankingCarryOverScheduler scheduler;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final String todayDate = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_FORMAT);
    private final String yesterdayDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).format(DATE_FORMAT);
    private final String todayKey = "ranking:all:" + todayDate;
    private final String yesterdayKey = "ranking:all:" + yesterdayDate;
    private final String flagKey = "ranking:carryover:done:" + todayDate;

    @BeforeEach
    void setUp() {
        productRankingRepository = mock(ProductRankingRepository.class);
        scheduler = new RankingCarryOverScheduler(productRankingRepository);
    }

    @DisplayName("어제 랭킹 데이터가 있고 플래그가 없으면 todayKey 자기 자신을 보존한 채 어제 점수의 10%를 합산한다")
    @Test
    void carryOver_preservesTodayScoresAndAddsCarryOver() {
        when(productRankingRepository.exists(yesterdayKey)).thenReturn(true);
        when(productRankingRepository.markIfAbsent(flagKey, 172_800)).thenReturn(true);

        scheduler.carryOver();

        verify(productRankingRepository).unionStoreWithWeights(todayKey, 1.0, yesterdayKey, 0.1);
        verify(productRankingRepository).setTtlIfAbsent(todayKey, 172_800);
    }

    @DisplayName("어제 랭킹 데이터가 없으면 carry-over를 생략한다")
    @Test
    void carryOver_noYesterdayData_skips() {
        when(productRankingRepository.exists(yesterdayKey)).thenReturn(false);

        scheduler.carryOver();

        verify(productRankingRepository, never()).markIfAbsent(anyString(), anyLong());
        verify(productRankingRepository, never()).unionStoreWithWeights(anyString(), anyDouble(), anyString(), anyDouble());
    }

    @DisplayName("플래그 키가 이미 존재하면 carry-over를 생략한다 (멱등성)")
    @Test
    void carryOver_flagAlreadySet_skips() {
        when(productRankingRepository.exists(yesterdayKey)).thenReturn(true);
        when(productRankingRepository.markIfAbsent(flagKey, 172_800)).thenReturn(false);

        scheduler.carryOver();

        verify(productRankingRepository, never()).unionStoreWithWeights(anyString(), anyDouble(), anyString(), anyDouble());
    }

    @DisplayName("todayKey에 이미 이벤트 점수가 쌓여있어도 보존된다 (00:00~00:05 사이 이벤트)")
    @Test
    void carryOver_existingTodayScoresArePreserved() {
        when(productRankingRepository.exists(yesterdayKey)).thenReturn(true);
        when(productRankingRepository.markIfAbsent(flagKey, 172_800)).thenReturn(true);

        scheduler.carryOver();

        // todayKey에 weight 1.0을 적용하여 자기 자신을 보존
        verify(productRankingRepository).unionStoreWithWeights(
            eq(todayKey), eq(1.0), eq(yesterdayKey), eq(0.1)
        );
    }
}
