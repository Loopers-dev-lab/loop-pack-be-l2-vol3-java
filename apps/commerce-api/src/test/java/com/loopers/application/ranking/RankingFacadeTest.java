package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.ranking.RankingCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RankingFacadeTest {

    @Mock
    private RankingCacheService rankingCacheService;

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    private RankingFacade rankingFacade;

    @BeforeEach
    void setUp() {
        rankingFacade = new RankingFacade(rankingCacheService, productService, brandService);
    }

    @DisplayName("랭킹 목록 조회")
    @Nested
    class GetRankings {

        @DisplayName("ZSET에 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoRankingData() {
            // Arrange
            given(rankingCacheService.getProductIds("20260408", 0, 20)).willReturn(List.of());

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260408", 1, 20);

            // Assert
            assertThat(result).isEmpty();
        }

        @DisplayName("ZSET 순서대로 rank가 1부터 부여된다.")
        @Test
        void assignsRankInOrder_whenRankingDataExists() {
            // Arrange
            Product productA = new Product(1L, "상품A", 10000, 100, "설명A", "https://img.a");
            Product productB = new Product(2L, "상품B", 5000, 50, "설명B", "https://img.b");
            Brand brand = new Brand("브랜드X", "브랜드 설명");

            given(rankingCacheService.getProductIds("20260408", 0, 20))
                .willReturn(List.of("1", "2")); // 1번이 더 높은 점수
            given(productService.getProductsByIds(List.of(1L, 2L)))
                .willReturn(List.of(productA, productB));
            given(brandService.getBrandsByIds(List.of(1L))).willReturn(List.of(brand));

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260408", 1, 20);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).rank()).isEqualTo(1L);
            assertThat(result.get(1).rank()).isEqualTo(2L);
            assertThat(result.get(0).productId()).isEqualTo(1L);
            assertThat(result.get(1).productId()).isEqualTo(2L);
        }

        @DisplayName("page=2 요청 시 rank가 size+1부터 시작된다.")
        @Test
        void assignsRankFromOffset_whenPage2Requested() {
            // Arrange
            Product productC = new Product(3L, "상품C", 3000, 30, "설명C", "https://img.c");
            Brand brand = new Brand("브랜드X", "브랜드 설명");

            given(rankingCacheService.getProductIds("20260408", 20, 20))
                .willReturn(List.of("3"));
            given(productService.getProductsByIds(List.of(3L))).willReturn(List.of(productC));
            given(brandService.getBrandsByIds(List.of(1L))).willReturn(List.of(brand));

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260408", 2, 20);

            // Assert
            assertThat(result.get(0).rank()).isEqualTo(21L);
        }
    }

    @DisplayName("랭킹 전체 개수 조회")
    @Nested
    class GetTotalCount {

        @DisplayName("ZSET에 상품이 3개이면 3을 반환한다.")
        @Test
        void returnsTotalCount() {
            // Arrange
            given(rankingCacheService.getTotalCount("20260408")).willReturn(3L);

            // Act
            long count = rankingFacade.getTotalCount("20260408");

            // Assert
            assertThat(count).isEqualTo(3L);
        }
    }
}
