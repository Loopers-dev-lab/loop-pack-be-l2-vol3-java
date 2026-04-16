package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.InMemoryRankingRepository;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.ProductRankSnapshotQueryRepository;
import com.loopers.domain.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingType;
import com.loopers.event.ranking.RankingKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RankingFacadeTest {

    private static final LocalDate FIXED_DATE = LocalDate.of(2025, 4, 9);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_DATE.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul")
    );

    private InMemoryRankingRepository rankingRepository;
    private ProductRankSnapshotQueryRepository snapshotQueryRepository;
    private ProductFacade productFacade;
    private RankingFacade rankingFacade;

    @BeforeEach
    void setUp() {
        rankingRepository = new InMemoryRankingRepository();
        snapshotQueryRepository = mock(ProductRankSnapshotQueryRepository.class);
        productFacade = mock(ProductFacade.class);
        rankingFacade = new RankingFacade(rankingRepository, snapshotQueryRepository, productFacade, FIXED_CLOCK);
    }

    private ProductInfo stubProduct(Long id, String name, int price) {
        return new ProductInfo(id, new ProductInfo.BrandSummary(1L, "브랜드"), name, "설명",
                price, 100, 0, Product.Visibility.VISIBLE, null, null, null);
    }

    @DisplayName("일간 랭킹을 조회하면, ")
    @Nested
    class DailyRankings {

        @DisplayName("Redis에서 점수 높은 순으로 상품 정보와 함께 반환한다.")
        @Test
        void getRankings_returnsRankedProductInfo() {
            // arrange
            LocalDate date = LocalDate.of(2025, 4, 9);
            String key = RankingKeyGenerator.keyOf(date);
            long highestRankedProductId = 1L;
            long secondRankedProductId = 3L;
            long lowerRankedProductId = 2L;
            double highestScore = 300.0;
            double secondScore = 200.0;
            double lowerScore = 100.0;
            int highestProductPrice = 10_000;
            int secondProductPrice = 30_000;

            rankingRepository.addScore(key, highestRankedProductId, highestScore);
            rankingRepository.addScore(key, lowerRankedProductId, lowerScore);
            rankingRepository.addScore(key, secondRankedProductId, secondScore);

            given(productFacade.getActiveProduct(highestRankedProductId))
                    .willReturn(stubProduct(highestRankedProductId, "상품A", highestProductPrice));
            given(productFacade.getActiveProduct(secondRankedProductId))
                    .willReturn(stubProduct(secondRankedProductId, "상품C", secondProductPrice));

            // act
            RankingPageResult result = rankingFacade.getRankings(date, RankingType.DAILY, 1, 2);

            // assert
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(2);
            assertThat(result.items())
                    .extracting(
                            RankingProductInfo::productId,
                            RankingProductInfo::productName,
                            RankingProductInfo::price,
                            RankingProductInfo::brandName,
                            RankingProductInfo::rank,
                            RankingProductInfo::score
                    )
                    .containsExactly(
                            tuple(highestRankedProductId, "상품A", highestProductPrice, "브랜드", 1L, highestScore),
                            tuple(secondRankedProductId, "상품C", secondProductPrice, "브랜드", 2L, secondScore)
                    );
        }

        @DisplayName("해당 날짜에 데이터가 없으면 빈 리스트를 반환한다.")
        @Test
        void getRankings_emptyRanking() {
            // act
            RankingPageResult result = rankingFacade.getRankings(LocalDate.of(2025, 1, 1), RankingType.DAILY, 1, 20);

            // assert
            assertThat(result.items()).isEmpty();
            assertThat(result.page()).isEqualTo(1);
            assertThat(result.size()).isEqualTo(20);
        }

        @DisplayName("삭제된 상품이 랭킹에 남아있으면 해당 항목을 건너뛰고 나머지를 반환한다.")
        @Test
        void getRankings_skipsStaleEntry() {
            // arrange
            LocalDate date = FIXED_DATE;
            String key = RankingKeyGenerator.keyOf(date);
            long activeProductId = 1L;
            long deletedProductId = 2L;

            rankingRepository.addScore(key, deletedProductId, 500.0);
            rankingRepository.addScore(key, activeProductId, 300.0);

            given(productFacade.getActiveProduct(deletedProductId))
                    .willThrow(new com.loopers.support.error.CoreException(
                            com.loopers.support.error.ErrorType.NOT_FOUND, "삭제된 상품"));
            given(productFacade.getActiveProduct(activeProductId))
                    .willReturn(stubProduct(activeProductId, "활성상품", 10_000));

            // act
            RankingPageResult result = rankingFacade.getRankings(date, RankingType.DAILY, 1, 10);

            // assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(activeProductId);
        }
    }

    @DisplayName("특정 상품의 오늘 랭킹을 조회한다. (1-based)")
    @Test
    void getProductRank_returns1BasedRank() {
        // arrange
        String key = RankingKeyGenerator.keyOf(FIXED_DATE);
        long topRankedProductId = 1L;
        long targetProductId = 3L;
        long lowerRankedProductId = 2L;

        rankingRepository.addScore(key, topRankedProductId, 300.0);
        rankingRepository.addScore(key, lowerRankedProductId, 100.0);
        rankingRepository.addScore(key, targetProductId, 200.0);

        // act
        RankingInfo result = rankingFacade.getProductRank(targetProductId);

        // assert
        assertThat(result.rank()).isEqualTo(2L);
        assertThat(result.score()).isEqualTo(200.0);
    }

    @DisplayName("랭킹에 없는 상품 조회 시 null을 반환한다.")
    @Test
    void getProductRank_returnsNullWhenNotRanked() {
        // arrange
        String key = RankingKeyGenerator.keyOf(FIXED_DATE);
        rankingRepository.addScore(key, 1L, 300.0);

        // act
        RankingInfo result = rankingFacade.getProductRank(999L);

        // assert
        assertThat(result).isNull();
    }

    @DisplayName("주간/월간 랭킹을 조회하면, ")
    @Nested
    class SnapshotRankings {

        @DisplayName("date 미지정 시 가장 최근 배치 결과(rank_date)의 스냅샷을 반환한다.")
        @Test
        void getWeeklyRankings_returnsSnapshotData() {
            // arrange
            LocalDate rankDate = LocalDate.of(2025, 4, 8);
            given(snapshotQueryRepository.findLatestRankDate(RankingType.WEEKLY))
                    .willReturn(Optional.of(rankDate));
            given(snapshotQueryRepository.findRankings(RankingType.WEEKLY, rankDate, 0, 10))
                    .willReturn(List.of(
                            createSnapshot(RankingType.WEEKLY, rankDate, 1, 101L, "상품A", 10000, "브랜드A", 500.0),
                            createSnapshot(RankingType.WEEKLY, rankDate, 2, 102L, "상품B", 20000, "브랜드B", 300.0)
                    ));

            // act
            RankingPageResult result = rankingFacade.getRankings(null, RankingType.WEEKLY, 1, 10);

            // assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.items())
                    .extracting(RankingProductInfo::productId, RankingProductInfo::rank, RankingProductInfo::score)
                    .containsExactly(
                            tuple(101L, 1L, 500.0),
                            tuple(102L, 2L, 300.0)
                    );
        }

        @DisplayName("date 지정 시 해당 날짜의 스냅샷을 반환한다.")
        @Test
        void getMonthlyRankings_withSpecificDate() {
            // arrange
            LocalDate specificDate = LocalDate.of(2025, 3, 31);
            given(snapshotQueryRepository.findRankings(RankingType.MONTHLY, specificDate, 0, 5))
                    .willReturn(List.of(
                            createSnapshot(RankingType.MONTHLY, specificDate, 1, 201L, "인기상품", 50000, "인기브랜드", 1000.0)
                    ));

            // act
            RankingPageResult result = rankingFacade.getRankings(specificDate, RankingType.MONTHLY, 1, 5);

            // assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productName()).isEqualTo("인기상품");
            assertThat(result.items().get(0).brandName()).isEqualTo("인기브랜드");
        }

        @DisplayName("배치 결과가 없으면 빈 리스트를 반환한다.")
        @Test
        void getWeeklyRankings_returnsEmptyWhenNoData() {
            // arrange
            given(snapshotQueryRepository.findLatestRankDate(RankingType.WEEKLY))
                    .willReturn(Optional.empty());

            // act
            RankingPageResult result = rankingFacade.getRankings(null, RankingType.WEEKLY, 1, 10);

            // assert
            assertThat(result.items()).isEmpty();
            assertThat(result.page()).isEqualTo(1);
        }

        @DisplayName("page/size에 따라 offset 기반 페이지네이션이 동작한다.")
        @Test
        void getWeeklyRankings_pagination() {
            // arrange
            LocalDate rankDate = LocalDate.of(2025, 4, 8);
            given(snapshotQueryRepository.findLatestRankDate(RankingType.WEEKLY))
                    .willReturn(Optional.of(rankDate));
            given(snapshotQueryRepository.findRankings(RankingType.WEEKLY, rankDate, 2, 2))
                    .willReturn(List.of(
                            createSnapshot(RankingType.WEEKLY, rankDate, 3, 103L, "상품C", 30000, "브랜드C", 100.0)
                    ));

            // act
            RankingPageResult result = rankingFacade.getRankings(null, RankingType.WEEKLY, 2, 2);

            // assert
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).rank()).isEqualTo(3L);
            assertThat(result.page()).isEqualTo(2);
        }

        private ProductRankSnapshot createSnapshot(RankingType type, LocalDate rankDate, int position,
                                                   Long productId, String name, int price, String brand, double score) {
            return ProductRankSnapshot.builder()
                    .rankingType(type)
                    .rankDate(rankDate)
                    .rankPosition(position)
                    .productId(productId)
                    .productName(name)
                    .price(price)
                    .brandName(brand)
                    .totalViewCount(0L)
                    .totalLikeCount(0L)
                    .totalOrderLineCount(0L)
                    .totalOrderAmount(0L)
                    .score(score)
                    .build();
        }
    }
}
