package com.loopers.application.ranking;

import com.loopers.domain.product.ProductService;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingFacade 단위 테스트")
class RankingFacadeTest {

    @Mock private RankingService rankingService;
    @Mock private ProductService productService;
    @Mock private BrandService brandService;

    @InjectMocks
    private RankingFacade rankingFacade;

    private static final LocalDate DATE = LocalDate.of(2026, 4, 7);

    @Test
    @DisplayName("getDailyRankings — ZSET 결과가 비어있으면 빈 페이지 반환")
    void getDailyRankings_EmptyZset_ReturnsEmptyPage() {
        when(rankingService.getTopRankings(eq(DATE), eq(0), anyInt()))
                .thenReturn(List.of());

        MvRankingPage result = rankingFacade.getDailyRankings(DATE, 0, 20);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("getDailyRankings — ZSET 전체가 MAX_RANK(100)을 초과하면 totalElements는 100으로 캡된다")
    void getDailyRankings_TotalCountExceedsMaxRank_CapsTotalElementsTo100() {
        RankingRepository.RankingEntry entry = new RankingRepository.RankingEntry(1L, 10.0);
        when(rankingService.getTopRankings(eq(DATE), eq(0), anyInt()))
                .thenReturn(List.of(entry));
        when(productService.findAllByIds(List.of(1L)))
                .thenReturn(List.of());
        when(rankingService.getTotalCount(DATE)).thenReturn(5000L);

        MvRankingPage result = rankingFacade.getDailyRankings(DATE, 0, 20);

        assertThat(result.totalElements()).isEqualTo(100L);
        assertThat(result.totalPages()).isEqualTo(5);
    }
}
