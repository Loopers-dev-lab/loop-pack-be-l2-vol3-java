package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.application.product.cache.ProductCacheReader;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductEvent;
import com.loopers.domain.product.ProductEventPublisher;
import com.loopers.domain.product.ProductName;
import com.loopers.domain.product.ProductThumbnailUrl;
import com.loopers.domain.product.Stock;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.shared.Money;

@ExtendWith(MockitoExtension.class)
class ReadActiveProductDetailUseCaseTest {

    @InjectMocks
    private ReadActiveProductDetailUseCase readActiveProductDetailUseCase;

    @Mock
    private ProductCacheReader productCacheReader;

    @Mock
    private BrandService brandService;

    @Mock
    private LikeService likeService;

    @Mock
    private ProductEventPublisher productEventPublisher;

    @Mock
    private RankingRepository rankingRepository;

    @Mock
    private Product product;

    @Mock
    private Brand brand;

    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    class Execute {

        @DisplayName("ProductViewed 이벤트를 발행한다.")
        @Test
        void publishesProductViewedEvent() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            stubDependencies(userId, productId, false);
            given(rankingRepository.findRank(anyString(), anyLong())).willReturn(null);

            // act
            readActiveProductDetailUseCase.execute(userId, productId);

            // assert
            then(productEventPublisher).should().publishEvent(
                    argThat(event -> {
                        ProductEvent.ProductViewed viewed = (ProductEvent.ProductViewed) event;
                        return viewed.productId().equals(productId) && viewed.eventId() != null;
                    })
            );
        }

        @DisplayName("상품, 브랜드, 좋아요 정보를 조합하여 반환한다.")
        @Test
        void returnsProductDetail() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            stubDependencies(userId, productId, true);
            given(rankingRepository.findRank(anyString(), anyLong())).willReturn(null);

            // act
            ProductDetail result = readActiveProductDetailUseCase.execute(userId, productId);

            // assert
            assertAll(
                    () -> assertThat(result.productId()).isEqualTo(productId),
                    () -> assertThat(result.liked()).isTrue(),
                    () -> assertThat(result.rank()).isNull()
            );
        }

        @DisplayName("랭킹 순위가 있으면, rank를 포함하여 반환한다.")
        @Test
        void returnsProductDetailWithRank_whenRanked() {
            // arrange
            Long userId = 1L;
            Long productId = 100L;
            stubDependencies(userId, productId, false);
            given(rankingRepository.findRank(anyString(), anyLong())).willReturn(3);

            // act
            ProductDetail result = readActiveProductDetailUseCase.execute(userId, productId);

            // assert
            assertAll(
                    () -> assertThat(result.productId()).isEqualTo(productId),
                    () -> assertThat(result.rank()).isEqualTo(3)
            );
        }

        private void stubDependencies(Long userId, Long productId, boolean liked) {
            given(productCacheReader.readActiveProduct(productId)).willReturn(product);
            given(product.getBrandId()).willReturn(1L);
            given(product.getId()).willReturn(productId);
            given(product.getName()).willReturn(new ProductName("상품명"));
            given(product.getThumbnailUrl()).willReturn(new ProductThumbnailUrl("https://example.com/thumb.png"));
            given(product.getPrice()).willReturn(Money.wons(10000L));
            given(product.getStock()).willReturn(new Stock(100L));
            given(product.getDescription()).willReturn("설명");
            given(product.getLikeCount()).willReturn(0L);
            given(brandService.getActiveBrand(1L)).willReturn(brand);
            given(brand.getId()).willReturn(1L);
            given(brand.getName()).willReturn("브랜드명");
            given(brand.getLogoUrl()).willReturn("https://example.com/logo.png");
            given(likeService.isLiked(userId, productId)).willReturn(liked);
        }
    }
}
