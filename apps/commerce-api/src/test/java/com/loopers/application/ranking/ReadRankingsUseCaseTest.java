package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.application.product.cache.ProductCacheReader;
import com.loopers.domain.product.ProductFixture;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PageSize;

@ExtendWith(MockitoExtension.class)
class ReadRankingsUseCaseTest {

    private ReadRankingsUseCase readRankingsUseCase;

    @Mock
    private RankingService rankingService;

    @Mock
    private ProductCacheReader productCacheReader;

    @BeforeEach
    void setUp() {
        readRankingsUseCase = new ReadRankingsUseCase(rankingService, productCacheReader);
    }

    @DisplayName("랭킹을 조회할 때,")
    @Nested
    class Execute {

        @DisplayName("정상 데이터가 있으면, 순위·상품·score를 조합하여 반환한다.")
        @Test
        void returnsRankedProducts_whenDataExists() {
            // arrange
            var items = List.of(
                    new RankingItem(1, 42L, 70.0),
                    new RankingItem(2, 15L, 58.4)
            );
            given(rankingService.readTopRanked(anyString(), anyInt(), anyInt())).willReturn(items);
            given(rankingService.countAll(anyString())).willReturn(150L);

            var product42 = ProductFixture.createProduct(42L);
            var product15 = ProductFixture.createProduct(15L);
            given(productCacheReader.readActiveProductsByIds(List.of(42L, 15L)))
                    .willReturn(List.of(product42, product15));

            // act
            RankingPageResult result = readRankingsUseCase.execute("20250406", new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.rankings()).hasSize(2),
                    () -> assertThat(result.rankings().get(0).rank()).isEqualTo(1),
                    () -> assertThat(result.rankings().get(0).productId()).isEqualTo(42L),
                    () -> assertThat(result.page()).isEqualTo(0),
                    () -> assertThat(result.size()).isEqualTo(20),
                    () -> assertThat(result.totalCount()).isEqualTo(150L)
            );
        }

        @DisplayName("date가 null이면, 오늘 날짜 기반으로 조회한다.")
        @Test
        void usesTodayDate_whenDateIsNull() {
            // arrange
            given(rankingService.readTopRanked(any(), anyInt(), anyInt())).willReturn(List.of());
            given(rankingService.countAll(any())).willReturn(0L);

            // act
            RankingPageResult result = readRankingsUseCase.execute(null, new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.rankings()).isEmpty(),
                    () -> assertThat(result.totalCount()).isZero()
            );
        }

        @DisplayName("빈 랭킹이면, 빈 결과를 반환한다.")
        @Test
        void returnsEmptyResult_whenNoRankings() {
            // arrange
            given(rankingService.readTopRanked(anyString(), anyInt(), anyInt())).willReturn(List.of());
            given(rankingService.countAll(anyString())).willReturn(0L);

            // act
            RankingPageResult result = readRankingsUseCase.execute("20250406", new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.rankings()).isEmpty(),
                    () -> assertThat(result.page()).isEqualTo(0),
                    () -> assertThat(result.size()).isEqualTo(20),
                    () -> assertThat(result.totalCount()).isZero()
            );
        }
    }
}
