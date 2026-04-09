package com.loopers.application.ranking;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.ranking.cache.RankingProductCacheItem;
import com.loopers.domain.product.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingQueryFacadeTest {

    @Mock
    private RankingApplicationService rankingApplicationService;

    @Mock
    private ProductApplicationService productApplicationService;

    @Mock
    private BrandApplicationService brandApplicationService;

    @Mock
    private RankingProductCacheApplicationService rankingProductCacheApplicationService;

    @InjectMocks
    private RankingQueryFacade rankingQueryFacade;

    @Test
    @DisplayName("랭킹 상품 메타가 모두 캐시에 있으면 DB 상품 조회 없이 응답을 조합한다")
    void getDailyPageUsesRankingProductCache() {
        UUID productId = UUID.randomUUID();
        UUID brandId = UUID.randomUUID();
        LocalDate metricDate = LocalDate.of(2025, 9, 7);
        RankingProductView rankingProductView = new RankingProductView(productId, 1L, 12.3d);
        RankingProductCacheItem cachedItem = new RankingProductCacheItem(productId, "사료A", 10000, brandId, 7);

        given(rankingApplicationService.getDailyPage(metricDate, 1, 20)).willReturn(List.of(rankingProductView));
        given(rankingProductCacheApplicationService.findAll(List.of(productId))).willReturn(Map.of(productId, cachedItem));
        given(brandApplicationService.findNamesByIds(List.of(brandId))).willReturn(Map.of(brandId, "퍼피박스"));

        List<TopRankingProductView> result = rankingQueryFacade.getDailyPage(metricDate, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productId()).isEqualTo(productId);
        assertThat(result.get(0).name()).isEqualTo("사료A");
        assertThat(result.get(0).likeCount()).isEqualTo(7);
        assertThat(result.get(0).rank()).isEqualTo(1L);
        assertThat(result.get(0).score()).isEqualTo(12.3d);
        verify(productApplicationService, never()).findAllByIds(List.of(productId));
    }

    @Test
    @DisplayName("캐시에 없는 랭킹 상품 메타는 DB에서 일괄 조회 후 캐시에 저장한다")
    void getDailyPageLoadsMissingProductsAndCachesThem() {
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        UUID firstBrandId = UUID.randomUUID();
        UUID secondBrandId = UUID.randomUUID();
        LocalDate metricDate = LocalDate.of(2025, 9, 7);
        RankingProductView firstRankingView = new RankingProductView(firstProductId, 1L, 10.0d);
        RankingProductView secondRankingView = new RankingProductView(secondProductId, 2L, 9.0d);
        RankingProductCacheItem cachedItem = new RankingProductCacheItem(firstProductId, "사료A", 10000, firstBrandId, 5);
        Product secondProduct = new Product(secondProductId, "사료B", 9000, 20, "설명", UUID.randomUUID(), secondBrandId, 3, null);
        RankingProductCacheItem secondCacheItem = RankingProductCacheItem.from(secondProduct);

        given(rankingApplicationService.getDailyPage(metricDate, 1, 20)).willReturn(List.of(firstRankingView, secondRankingView));
        given(rankingProductCacheApplicationService.findAll(List.of(firstProductId, secondProductId)))
                .willReturn(Map.of(firstProductId, cachedItem));
        given(productApplicationService.findAllByIds(List.of(secondProductId))).willReturn(List.of(secondProduct));
        given(brandApplicationService.findNamesByIds(List.of(firstBrandId, secondBrandId)))
                .willReturn(Map.of(firstBrandId, "브랜드A", secondBrandId, "브랜드B"));

        List<TopRankingProductView> result = rankingQueryFacade.getDailyPage(metricDate, 1, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(1).productId()).isEqualTo(secondProductId);
        assertThat(result.get(1).name()).isEqualTo("사료B");
        assertThat(result.get(1).likeCount()).isEqualTo(3);
        verify(productApplicationService).findAllByIds(List.of(secondProductId));
        verify(rankingProductCacheApplicationService).saveAll(List.of(secondCacheItem));
    }
}
