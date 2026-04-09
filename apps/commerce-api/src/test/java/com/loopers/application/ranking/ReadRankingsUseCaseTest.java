package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.application.product.cache.ProductCacheReader;
import com.loopers.domain.like.LikeService;
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

    @Mock
    private LikeService likeService;

    @BeforeEach
    void setUp() {
        readRankingsUseCase = new ReadRankingsUseCase(rankingService, productCacheReader, likeService);
    }

    @DisplayName("랭킹을 조회할 때,")
    @Nested
    class Execute {

        @DisplayName("정상 데이터가 있으면, 순위·상품·brandId·liked를 조합하여 반환한다.")
        @Test
        void returnsRankedProducts_whenDataExists() {
            // arrange
            Long userId = 100L;
            var items = List.of(
                    new RankingItem(1, 42L, 70.0),
                    new RankingItem(2, 15L, 58.4)
            );
            given(rankingService.readTopRanked(anyString(), anyInt(), anyInt())).willReturn(items);

            var product42 = ProductFixture.createProduct(42L);
            var product15 = ProductFixture.createProduct(15L);
            given(productCacheReader.readActiveProductsByIds(List.of(42L, 15L)))
                    .willReturn(List.of(product42, product15));
            given(likeService.getLikedProductIds(userId, List.of(42L, 15L)))
                    .willReturn(Set.of(42L));

            // act
            RankingPageResult result = readRankingsUseCase.execute(userId, "20250406", new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.rankings()).hasSize(2),
                    () -> assertThat(result.rankings().get(0).rank()).isEqualTo(1),
                    () -> assertThat(result.rankings().get(0).productId()).isEqualTo(42L),
                    () -> assertThat(result.rankings().get(0).brandId()).isEqualTo(1L),
                    () -> assertThat(result.rankings().get(0).liked()).isTrue(),
                    () -> assertThat(result.rankings().get(1).liked()).isFalse(),
                    () -> assertThat(result.page()).isEqualTo(0),
                    () -> assertThat(result.size()).isEqualTo(20)
            );
        }

        @DisplayName("비로그인 사용자이면, liked는 모두 false이다.")
        @Test
        void returnsAllNotLiked_whenUserIsNull() {
            // arrange
            var items = List.of(new RankingItem(1, 42L, 70.0));
            given(rankingService.readTopRanked(anyString(), anyInt(), anyInt())).willReturn(items);

            var product42 = ProductFixture.createProduct(42L);
            given(productCacheReader.readActiveProductsByIds(List.of(42L)))
                    .willReturn(List.of(product42));
            given(likeService.getLikedProductIds(null, List.of(42L)))
                    .willReturn(Collections.emptySet());

            // act
            RankingPageResult result = readRankingsUseCase.execute(null, "20250406", new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.rankings()).hasSize(1),
                    () -> assertThat(result.rankings().get(0).liked()).isFalse()
            );
        }

        @DisplayName("빈 랭킹이면, 빈 결과를 반환한다.")
        @Test
        void returnsEmptyResult_whenNoRankings() {
            // arrange
            given(rankingService.readTopRanked(anyString(), anyInt(), anyInt())).willReturn(List.of());

            // act
            RankingPageResult result = readRankingsUseCase.execute(null, "20250406", new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.rankings()).isEmpty(),
                    () -> assertThat(result.page()).isEqualTo(0),
                    () -> assertThat(result.size()).isEqualTo(20)
            );
        }
    }
}
