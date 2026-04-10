package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.InMemoryRankingRepository;
import com.loopers.domain.ranking.RankingInfo;
import com.loopers.event.ranking.RankingKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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
    private ProductFacade productFacade;
    private RankingFacade rankingFacade;

    @BeforeEach
    void setUp() {
        rankingRepository = new InMemoryRankingRepository();
        productFacade = mock(ProductFacade.class);
        rankingFacade = new RankingFacade(rankingRepository, productFacade, FIXED_CLOCK);
    }

    private ProductInfo stubProduct(Long id, String name, int price) {
        return new ProductInfo(id, new ProductInfo.BrandSummary(1L, "브랜드"), name, "설명",
                price, 100, 0, Product.Visibility.VISIBLE, null, null, null);
    }

    @DisplayName("랭킹 페이지 조회 시 점수 높은 순으로 상품 정보와 함께 반환한다.")
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
        RankingPageResult result = rankingFacade.getRankings(date, 1, 2);

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

    @DisplayName("빈 랭킹 조회 시 빈 리스트를 반환한다.")
    @Test
    void getRankings_emptyRanking() {
        int requestedPage = 1;
        int requestedSize = 20;

        // act
        RankingPageResult result = rankingFacade.getRankings(LocalDate.of(2025, 1, 1), requestedPage, requestedSize);

        // assert
        assertThat(result.items()).isEmpty();
        assertThat(result.page()).isEqualTo(requestedPage);
        assertThat(result.size()).isEqualTo(requestedSize);
    }

    @DisplayName("특정 상품의 오늘 랭킹을 조회한다. (1-based)")
    @Test
    void getProductRank_returns1BasedRank() {
        // arrange
        String key = RankingKeyGenerator.keyOf(FIXED_DATE);
        long topRankedProductId = 1L;
        long targetProductId = 3L;
        long lowerRankedProductId = 2L;
        double topScore = 300.0;
        double targetScore = 200.0;
        double lowerScore = 100.0;

        rankingRepository.addScore(key, topRankedProductId, topScore);
        rankingRepository.addScore(key, lowerRankedProductId, lowerScore);
        rankingRepository.addScore(key, targetProductId, targetScore);

        // act
        RankingInfo result = rankingFacade.getProductRank(targetProductId);

        // assert
        assertThat(result.rank()).isEqualTo(2L);
        assertThat(result.score()).isEqualTo(targetScore);
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
}
