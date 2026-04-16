package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.ProductRankMonthly;
import com.loopers.domain.ranking.ProductRankWeekly;
import com.loopers.infrastructure.ranking.ProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.ProductRankWeeklyJpaRepository;
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

    @Mock
    private ProductRankWeeklyJpaRepository productRankWeeklyJpaRepository;

    @Mock
    private ProductRankMonthlyJpaRepository productRankMonthlyJpaRepository;

    private RankingFacade rankingFacade;

    @BeforeEach
    void setUp() {
        rankingFacade = new RankingFacade(rankingCacheService, productService, brandService,
            productRankWeeklyJpaRepository, productRankMonthlyJpaRepository);
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
            List<RankingInfo> result = rankingFacade.getRankings("20260408", 1, 20, RankingType.DAILY);

            // Assert
            assertThat(result).isEmpty();
        }

        @DisplayName("ZSET 첫 번째 항목의 rank는 1이다.")
        @Test
        void assignsRankInOrder_whenRankingDataExists() {
            // Arrange
            // BaseEntity.id는 항상 0L(final)이므로, ZSET id도 "0"으로 맞춰 productMap 조회가 가능하게 한다.
            Product product = new Product(0L, "상품A", 10000, 100, "설명A", "https://img.a");
            Brand brand = new Brand("브랜드X", "브랜드 설명");

            given(rankingCacheService.getProductIds("20260408", 0, 20))
                .willReturn(List.of("0"));
            given(productService.getProductsByIds(List.of(0L)))
                .willReturn(List.of(product));
            given(brandService.getBrandsByIds(List.of(0L))).willReturn(List.of(brand));

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260408", 1, 20, RankingType.DAILY);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rank()).isEqualTo(1L);
        }

        @DisplayName("page=2 요청 시 rank가 size+1부터 시작된다.")
        @Test
        void assignsRankFromOffset_whenPage2Requested() {
            // Arrange
            Product product = new Product(0L, "상품C", 3000, 30, "설명C", "https://img.c");
            Brand brand = new Brand("브랜드X", "브랜드 설명");

            given(rankingCacheService.getProductIds("20260408", 20, 20))
                .willReturn(List.of("0"));
            given(productService.getProductsByIds(List.of(0L))).willReturn(List.of(product));
            given(brandService.getBrandsByIds(List.of(0L))).willReturn(List.of(brand));

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260408", 2, 20, RankingType.DAILY);

            // Assert
            assertThat(result.get(0).rank()).isEqualTo(21L);
        }

        @DisplayName("주간 랭킹 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoWeeklyRankingData() {
            // Arrange
            given(productRankWeeklyJpaRepository.findByYearWeekOrderByRankPosition("2026-W16"))
                .willReturn(List.of());

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260416", 1, 20, RankingType.WEEKLY);

            // Assert
            assertThat(result).isEmpty();
        }

        @DisplayName("주간 랭킹 데이터가 있으면 rank_position 순서대로 반환한다.")
        @Test
        void returnsWeeklyRankings_inRankPositionOrder() {
            // Arrange
            // BaseEntity의 id는 항상 0L이므로, productId도 0L로 맞춰 productMap 조회가 가능하게 한다.
            ProductRankWeekly rank1 = ProductRankWeekly.of(0L, 90.0, 10L, 5L, 20L, "2026-W16");
            rank1.assignRank(1);
            Product product = new Product(0L, "상품A", 10000, 100, "설명A", "https://img.a");
            Brand brand = new Brand("브랜드X", "브랜드 설명");

            given(productRankWeeklyJpaRepository.findByYearWeekOrderByRankPosition("2026-W16"))
                .willReturn(List.of(rank1));
            given(productService.getProductsByIds(List.of(0L))).willReturn(List.of(product));
            given(brandService.getBrandsByIds(List.of(0L))).willReturn(List.of(brand));

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260416", 1, 20, RankingType.WEEKLY);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rank()).isEqualTo(1L);
            assertThat(result.get(0).name()).isEqualTo("상품A");
        }

        @DisplayName("월간 랭킹 데이터가 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmpty_whenNoMonthlyRankingData() {
            // Arrange
            given(productRankMonthlyJpaRepository.findByYearMonthOrderByRankPosition("2026-04"))
                .willReturn(List.of());

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260416", 1, 20, RankingType.MONTHLY);

            // Assert
            assertThat(result).isEmpty();
        }

        @DisplayName("월간 랭킹 데이터가 있으면 rank_position 순서대로 반환한다.")
        @Test
        void returnsMonthlyRankings_inRankPositionOrder() {
            // Arrange
            ProductRankMonthly rank1 = ProductRankMonthly.of(0L, 80.0, 8L, 4L, 15L, "2026-04");
            rank1.assignRank(1);
            Product product = new Product(0L, "상품B", 5000, 50, "설명B", "https://img.b");
            Brand brand = new Brand("브랜드Y", "브랜드 설명");

            given(productRankMonthlyJpaRepository.findByYearMonthOrderByRankPosition("2026-04"))
                .willReturn(List.of(rank1));
            given(productService.getProductsByIds(List.of(0L))).willReturn(List.of(product));
            given(brandService.getBrandsByIds(List.of(0L))).willReturn(List.of(brand));

            // Act
            List<RankingInfo> result = rankingFacade.getRankings("20260416", 1, 20, RankingType.MONTHLY);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).rank()).isEqualTo(1L);
            assertThat(result.get(0).name()).isEqualTo("상품B");
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
            long count = rankingFacade.getTotalCount("20260408", RankingType.DAILY);

            // Assert
            assertThat(count).isEqualTo(3L);
        }

        @DisplayName("주간 랭킹 전체 개수를 반환한다.")
        @Test
        void returnsWeeklyTotalCount() {
            // Arrange
            given(productRankWeeklyJpaRepository.countByYearWeek("2026-W16")).willReturn(50L);

            // Act
            long count = rankingFacade.getTotalCount("20260416", RankingType.WEEKLY);

            // Assert
            assertThat(count).isEqualTo(50L);
        }

        @DisplayName("월간 랭킹 전체 개수를 반환한다.")
        @Test
        void returnsMonthlyTotalCount() {
            // Arrange
            given(productRankMonthlyJpaRepository.countByYearMonth("2026-04")).willReturn(100L);

            // Act
            long count = rankingFacade.getTotalCount("20260416", RankingType.MONTHLY);

            // Assert
            assertThat(count).isEqualTo(100L);
        }
    }
}
