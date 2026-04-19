package com.loopers.application.ranking;

import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MonthlyRankingFacade 단위 테스트")
class MonthlyRankingFacadeTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 12);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final Clock FIXED =
            Clock.fixed(TODAY.atStartOfDay(KST).plusHours(10).toInstant(), KST);

    private MonthlyRankingRepository monthlyRankingRepository;
    private RankingAssembler rankingAssembler;
    private MonthlyRankingFacade facade;

    @BeforeEach
    void setUp() {
        monthlyRankingRepository = mock(MonthlyRankingRepository.class);
        rankingAssembler = mock(RankingAssembler.class);
        facade = new MonthlyRankingFacade(monthlyRankingRepository, rankingAssembler, FIXED);
    }

    @Nested
    @DisplayName("getMonthlyRanking")
    class GetMonthlyRanking {

        @Test
        @DisplayName("조회한 total 과 entries 를 RankingAssembler 에 위임한다")
        void delegatesToAssembler() {
            // given
            List<RankingEntry> entries = List.of(
                    new RankingEntry(1L, 1L, 10.0),
                    new RankingEntry(2L, 2L, 5.0)
            );
            when(monthlyRankingRepository.getTotal(YESTERDAY)).thenReturn(2L);
            when(monthlyRankingRepository.getTopN(YESTERDAY, 1, 20)).thenReturn(entries);
            when(rankingAssembler.assemble(YESTERDAY, 2L, entries))
                    .thenReturn(new RankingPageResult(YESTERDAY, 2L, List.of()));

            // when
            facade.getMonthlyRanking(YESTERDAY, 1, 20);

            // then
            verify(rankingAssembler).assemble(YESTERDAY, 2L, entries);
        }

        @Test
        @DisplayName("date 가 null 이면 KST 어제 날짜 기준으로 저장소를 조회한다")
        void nullDateUsesYesterdayForQuery() {
            // given
            when(monthlyRankingRepository.getTopN(any(), anyInt(), anyInt())).thenReturn(List.of());
            when(rankingAssembler.assemble(any(), anyLong(), any()))
                    .thenReturn(new RankingPageResult(YESTERDAY, 0L, List.of()));

            // when
            RankingPageResult result = facade.getMonthlyRanking(null, 1, 20);

            // then — FIXED clock 은 2026-04-12, 어제 = 2026-04-11
            verify(monthlyRankingRepository).getTopN(YESTERDAY, 1, 20);
            assertThat(result.effectiveDate()).isEqualTo(YESTERDAY);
        }
    }
}
