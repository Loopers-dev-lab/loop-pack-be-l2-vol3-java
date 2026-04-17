package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingListSource;
import com.loopers.domain.ranking.RankingMvPeriod;
import com.loopers.domain.ranking.RankingPage;
import com.loopers.domain.ranking.RankingQueryService;
import com.loopers.domain.ranking.RankingRow;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Facade는 날짜 해석과 {@link RankingQueryService} 위임·Application DTO 매핑만 담당한다.
 */
@ExtendWith(MockitoExtension.class)
class RankingFacadeTest {

    @Mock
    private RankingQueryService rankingQueryService;

    @InjectMocks
    private RankingFacade rankingFacade;

    @Test
    @DisplayName("date 문자열을 파싱해 QueryService에 LocalDate로 넘긴다.")
    void getRankings_shouldDelegateWithParsedDate() {
        RankingPage domainPage = new RankingPage(
                List.of(new RankingRow(1, 1L, 1.0d, "n", BigDecimal.ONE, 1L, "b", 0L, 0)),
                1,
                20,
                1L,
                1,
                RankingListSource.REDIS_ZSET,
                null
        );
        when(rankingQueryService.loadPage(eq(LocalDate.of(2026, 4, 8)), eq(1), eq(20), eq(Optional.empty())))
                .thenReturn(domainPage);

        RankingListInfo out = rankingFacade.getRankings("20260408", 1, 20);

        verify(rankingQueryService).loadPage(LocalDate.of(2026, 4, 8), 1, 20, Optional.empty());
        assertThat(out.totalElements()).isEqualTo(1L);
        assertThat(out.dataSource()).isEqualTo("REDIS");
        assertThat(out.items()).hasSize(1);
        assertThat(out.items().get(0).productId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("잘못된 date 형식이면 BAD_REQUEST")
    void getRankings_whenInvalidDate_shouldThrow() {
        assertThatThrownBy(() -> rankingFacade.getRankings("bad", 1, 20))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("period·periodKey가 있으면 loadMvPage로 위임한다")
    void getRankings_whenWeeklyMv_shouldDelegateLoadMvPage() {
        RankingPage domainPage = new RankingPage(
                List.of(new RankingRow(1, 1L, 1.0d, "n", BigDecimal.ONE, 1L, "b", 0L, 0)),
                1,
                20,
                1L,
                1,
                RankingListSource.MV_WEEKLY,
                null
        );
        when(rankingQueryService.loadMvPage(RankingMvPeriod.WEEKLY, "2026W15", 1, 20))
                .thenReturn(domainPage);

        RankingListInfo out = rankingFacade.getRankings(
                null, "WEEKLY", "2026W15", 1, 20, Optional.empty());

        verify(rankingQueryService).loadMvPage(RankingMvPeriod.WEEKLY, "2026W15", 1, 20);

        assertThat(out.dataSource()).isEqualTo("MV_WEEKLY");
        assertThat(out.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("date와 period를 동시에 주면 BAD_REQUEST")
    void getRankings_whenDateAndPeriodTogether_shouldThrow() {
        assertThatThrownBy(() -> rankingFacade.getRankings(
                "20260408", "WEEKLY", "2026W15", 1, 20, Optional.empty()))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("주간 조회에 rankingSnapshotId를 주면 BAD_REQUEST")
    void getRankings_whenMvAndSnapshot_shouldThrow() {
        assertThatThrownBy(() -> rankingFacade.getRankings(
                null, "WEEKLY", "2026W15", 1, 20, Optional.of(UUID.randomUUID().toString())))
                .isInstanceOf(CoreException.class);
    }
}
