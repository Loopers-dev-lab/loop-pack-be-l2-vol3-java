package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PagedResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
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
    @DisplayName("getRankings — ZSET 결과가 비어있으면 빈 페이지 반환")
    void getRankings_EmptyZset_ReturnsEmptyPage() {
        when(rankingService.getTopRankings(eq(DATE), eq(0), anyInt()))
                .thenReturn(List.of());

        PagedResult<RankingInfo> result = rankingFacade.getRankings(DATE, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }
}
