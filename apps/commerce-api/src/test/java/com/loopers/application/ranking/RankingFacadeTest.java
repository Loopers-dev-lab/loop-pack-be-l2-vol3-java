package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.ranking.dto.RankingCriteria;
import com.loopers.application.ranking.dto.RankingResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingScoreService;
import com.loopers.domain.ranking.dto.RankingInfo;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RankingFacadeTest {

    @Mock
    private RankingScoreService rankingScoreService;

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @InjectMocks
    private RankingFacade rankingFacade;

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 5);
    private static final Long BRAND_ID = 1L;

    @DisplayName("랭킹 목록을 조회할 때, ")
    @Nested
    class GetTopRankings {

        @DisplayName("점수 내림차순으로 랭킹이 반환된다.")
        @Test
        void getTopRankings_whenCalled_thenReturnedByScoreDesc() {
            // arrange
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(1, 100L, 7000.0),
                    new RankingInfo.RankedScore(2, 200L, 500.0),
                    new RankingInfo.RankedScore(3, 300L, 100.0));
            when(rankingScoreService.getTopRankedByDate(eq(TODAY), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(TODAY)).thenReturn(3L);
            stubProductsAndBrands(100L, 200L, 300L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(TODAY, 1, 20));

            // assert
            assertThat(result.items()).hasSize(3);
            assertThat(result.items().get(0).score()).isEqualTo(7000.0);
            assertThat(result.items().get(1).score()).isEqualTo(500.0);
            assertThat(result.items().get(2).score()).isEqualTo(100.0);
        }

        @DisplayName("응답에 상품 정보가 포함된다.")
        @Test
        void getTopRankings_whenCalled_thenIncludesProductInfo() {
            // arrange
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(1, 100L, 7000.0));
            when(rankingScoreService.getTopRankedByDate(eq(TODAY), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(TODAY)).thenReturn(1L);
            stubProductsAndBrands(100L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(TODAY, 1, 20));

            // assert
            RankingResult.RankingEntry entry = result.items().get(0);
            assertThat(entry.productName()).isEqualTo("상품_100");
            assertThat(entry.brandName()).isEqualTo("테스트브랜드");
            assertThat(entry.price()).isEqualTo(10000);
            assertThat(entry.thumbnailUrl()).isEqualTo("https://img.example.com/100.jpg");
        }

        @DisplayName("각 항목에 절대 순위 번호가 포함된다.")
        @Test
        void getTopRankings_whenCalled_thenIncludesAbsoluteRank() {
            // arrange
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(1, 100L, 7000.0),
                    new RankingInfo.RankedScore(2, 200L, 500.0));
            when(rankingScoreService.getTopRankedByDate(eq(TODAY), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(TODAY)).thenReturn(2L);
            stubProductsAndBrands(100L, 200L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(TODAY, 1, 20));

            // assert
            assertThat(result.items().get(0).rank()).isEqualTo(1);
            assertThat(result.items().get(1).rank()).isEqualTo(2);
        }

        @DisplayName("동점인 상품이 있어도 순위가 정상적으로 매겨진다.")
        @Test
        void getTopRankings_whenTiedScores_thenRankedProperly() {
            // arrange
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(1, 100L, 500.0),
                    new RankingInfo.RankedScore(1, 200L, 500.0),
                    new RankingInfo.RankedScore(3, 300L, 100.0));
            when(rankingScoreService.getTopRankedByDate(eq(TODAY), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(TODAY)).thenReturn(3L);
            stubProductsAndBrands(100L, 200L, 300L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(TODAY, 1, 20));

            // assert
            assertThat(result.items().get(0).rank()).isEqualTo(1);
            assertThat(result.items().get(1).rank()).isEqualTo(1);
            assertThat(result.items().get(2).rank()).isEqualTo(3);
        }

        @DisplayName("삭제된 상품은 제외하고 반환된다.")
        @Test
        void getTopRankings_whenDeletedProduct_thenExcluded() {
            // arrange
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(1, 100L, 7000.0),
                    new RankingInfo.RankedScore(2, 200L, 500.0));
            when(rankingScoreService.getTopRankedByDate(eq(TODAY), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(TODAY)).thenReturn(2L);
            // 200L 상품은 삭제되어 조회되지 않음
            stubProductsAndBrands(100L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(TODAY, 1, 20));

            // assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(100L);
        }
    }

    @DisplayName("상품 순위를 조회할 때, ")
    @Nested
    class GetProductRank {

        @DisplayName("랭킹에 진입한 상품이면 순위가 반환된다.")
        @Test
        void getProductRank_whenRanked_thenReturnsRank() {
            // arrange
            when(rankingScoreService.getRankByProductIdAndDate(100L, TODAY))
                    .thenReturn(Optional.of(3L));

            // act
            Optional<Long> rank = rankingFacade.getProductRank(100L, TODAY);

            // assert
            assertThat(rank).isPresent().contains(3L);
        }

        @DisplayName("랭킹에 진입하지 않은 상품이면 빈 값이 반환된다.")
        @Test
        void getProductRank_whenNotRanked_thenReturnsEmpty() {
            // arrange
            when(rankingScoreService.getRankByProductIdAndDate(999L, TODAY))
                    .thenReturn(Optional.empty());

            // act
            Optional<Long> rank = rankingFacade.getProductRank(999L, TODAY);

            // assert
            assertThat(rank).isEmpty();
        }
    }

    @DisplayName("페이지네이션으로 조회할 때, ")
    @Nested
    class Pagination {

        @DisplayName("첫 번째 페이지 조회 시 상위 N건이 반환된다.")
        @Test
        void getTopRankings_whenFirstPage_thenTopNReturned() {
            // arrange
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(1, 100L, 7000.0),
                    new RankingInfo.RankedScore(2, 200L, 500.0));
            when(rankingScoreService.getTopRankedByDate(eq(TODAY), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(TODAY)).thenReturn(5L);
            stubProductsAndBrands(100L, 200L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(TODAY, 1, 2));

            // assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.totalElements()).isEqualTo(5);
        }

        @DisplayName("다음 페이지 조회 시 이어지는 순위가 반환된다.")
        @Test
        void getTopRankings_whenSecondPage_thenNextItems() {
            // arrange
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(3, 300L, 100.0),
                    new RankingInfo.RankedScore(4, 400L, 50.0));
            when(rankingScoreService.getTopRankedByDate(eq(TODAY), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(TODAY)).thenReturn(5L);
            stubProductsAndBrands(300L, 400L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(TODAY, 2, 2));

            // assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).rank()).isEqualTo(3);
            assertThat(result.items().get(1).rank()).isEqualTo(4);
        }
    }

    @DisplayName("이전 날짜로 조회할 때, ")
    @Nested
    class DateQuery {

        @DisplayName("해당 날짜의 랭킹이 반환된다.")
        @Test
        void getTopRankings_whenPreviousDate_thenReturnsDateRanking() {
            // arrange
            LocalDate yesterday = TODAY.minusDays(1);
            List<RankingInfo.RankedScore> rankedScores = List.of(
                    new RankingInfo.RankedScore(1, 100L, 3000.0));
            when(rankingScoreService.getTopRankedByDate(eq(yesterday), any()))
                    .thenReturn(rankedScores);
            when(rankingScoreService.countByDate(yesterday)).thenReturn(1L);
            stubProductsAndBrands(100L);

            // act
            RankingResult.RankingPage result = rankingFacade.getTopRankings(
                    new RankingCriteria.Search(yesterday, 1, 20));

            // assert
            assertThat(result.date()).isEqualTo(yesterday);
            assertThat(result.items()).hasSize(1);
        }
    }

    @DisplayName("콜드 스타트 이월을 실행할 때, ")
    @Nested
    class CarryOverScores {

        @DisplayName("RankingScoreService에 이월을 위임한다.")
        @Test
        void carryOver_whenCalled_thenDelegatesToService() {
            // act
            rankingFacade.carryOverScores(TODAY, 0.1);

            // assert
            verify(rankingScoreService).carryOver(TODAY, 0.1);
        }
    }

    // === 헬퍼 === //

    private void stubProductsAndBrands(Long... productIds) {
        List<ProductModel> products = Arrays.stream(productIds)
                .map(this::createProduct)
                .toList();
        when(productService.findAllByIds(any())).thenReturn(products);

        Map<Long, String> brandNameMap = Arrays.stream(productIds)
                .collect(Collectors.toMap(id -> BRAND_ID, id -> "테스트브랜드", (a, b) -> a));
        when(brandService.getNameMapByIds(any())).thenReturn(brandNameMap);
    }

    private ProductModel createProduct(Long productId) {
        ProductModel product = ProductModel.create(BRAND_ID, "상품_" + productId, 10000, 100);
        product.updateThumbnailUrl("https://img.example.com/" + productId + ".jpg");
        setId(product, productId);
        return product;
    }

    private void setId(Object entity, Long id) {
        try {
            var idField = entity.getClass().getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
