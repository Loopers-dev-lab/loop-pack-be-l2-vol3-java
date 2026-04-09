package com.loopers.application.product;

import com.loopers.application.brand.BrandAppService;
import com.loopers.application.like.LikeAppService;
import com.loopers.application.ranking.RankingAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("ProductFacade 단위 테스트")
class ProductFacadeTest {

    private ProductFacade productFacade;
    private ProductAppService productAppService;
    private BrandAppService brandAppService;
    private LikeAppService likeAppService;
    private RankingAppService rankingAppService;
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        productAppService = mock(ProductAppService.class);
        brandAppService = mock(BrandAppService.class);
        likeAppService = mock(LikeAppService.class);
        rankingAppService = mock(RankingAppService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        productFacade = new ProductFacade(productAppService, brandAppService, likeAppService, rankingAppService, eventPublisher);
    }

    @Nested
    @DisplayName("상품 상세 조회")
    class GetProductDetailTest {

        @Test
        @DisplayName("상품 상세 정보를 조합하여 반환한다")
        void getProductDetail_success() {
            // given
            Long productId = 1L;
            Long brandId = 10L;
            Long userId = 100L;

            CachedProductDetail cachedDetail = CachedProductDetail.builder()
                    .productId(productId)
                    .productName("테스트 상품")
                    .basePrice(Money.of(10000L))
                    .deleted(false)
                    .brandId(brandId)
                    .likeCount(42L)
                    .options(List.of(ProductInfo.OptionInfo.builder()
                            .optionId(1L)
                            .optionName("기본")
                            .additionalPrice(Money.of(0L))
                            .stock(50)
                            .soldOut(false)
                            .build()))
                    .build();

            Brand brand = mock(Brand.class);
            given(brand.getId()).willReturn(brandId);
            given(brand.getName()).willReturn("테스트 브랜드");

            given(productAppService.getProductDetailCached(productId)).willReturn(cachedDetail);
            given(brandAppService.getById(brandId)).willReturn(brand);
            given(likeAppService.isLikedByUser(userId, productId)).willReturn(true);
            given(rankingAppService.getProductRank(any(), eq(productId))).willReturn(3L);

            // when
            ProductInfo result = productFacade.getProductDetail(productId, userId);

            // then
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getBrandName()).isEqualTo("테스트 브랜드");
            assertThat(result.getLikeCount()).isEqualTo(42L);
            assertThat(result.isLikedByUser()).isTrue();
            assertThat(result.getRank()).isEqualTo(3L);
            assertThat(result.getOptions()).hasSize(1);
        }

        @Test
        @DisplayName("비로그인 사용자는 likedByUser가 false로 반환된다")
        void getProductDetail_noUser() {
            // given
            Long productId = 1L;
            Long brandId = 10L;

            CachedProductDetail cachedDetail = CachedProductDetail.builder()
                    .productId(productId)
                    .productName("테스트 상품")
                    .basePrice(Money.of(10000L))
                    .deleted(false)
                    .brandId(brandId)
                    .likeCount(0L)
                    .options(List.of())
                    .build();

            Brand brand = mock(Brand.class);
            given(brand.getId()).willReturn(brandId);
            given(brand.getName()).willReturn("테스트 브랜드");

            given(productAppService.getProductDetailCached(productId)).willReturn(cachedDetail);
            given(brandAppService.getById(brandId)).willReturn(brand);
            given(rankingAppService.getProductRank(any(), eq(productId))).willReturn(null);

            // when
            ProductInfo result = productFacade.getProductDetail(productId, null);

            // then
            assertThat(result.isLikedByUser()).isFalse();
            assertThat(result.getRank()).isNull();
            verify(likeAppService, never()).isLikedByUser(any(), any());
        }

        @Test
        @DisplayName("랭킹 조회가 실패해도 상품 상세는 rank 없이 반환된다")
        void getProductDetail_rankingFailure_degradesGracefully() {
            Long productId = 1L;
            Long brandId = 10L;

            CachedProductDetail cachedDetail = CachedProductDetail.builder()
                    .productId(productId)
                    .productName("테스트 상품")
                    .basePrice(Money.of(10000L))
                    .deleted(false)
                    .brandId(brandId)
                    .likeCount(0L)
                    .options(List.of())
                    .build();

            Brand brand = mock(Brand.class);
            given(brand.getId()).willReturn(brandId);
            given(brand.getName()).willReturn("테스트 브랜드");

            given(productAppService.getProductDetailCached(productId)).willReturn(cachedDetail);
            given(brandAppService.getById(brandId)).willReturn(brand);
            given(rankingAppService.getProductRank(any(), eq(productId))).willThrow(new RuntimeException("redis down"));

            ProductInfo result = productFacade.getProductDetail(productId, null);

            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getBrandName()).isEqualTo("테스트 브랜드");
            assertThat(result.getRank()).isNull();
        }
    }

    @Nested
    @DisplayName("브랜드별 상품 목록 조회")
    class GetProductsByBrandTest {

        @Test
        @DisplayName("브랜드별 상품 목록을 조합하여 반환한다")
        void getProductsByBrand_success() {
            // given
            Long brandId = 10L;

            CachedBrandProductPage cachedPage = CachedBrandProductPage.builder()
                    .content(List.of(
                            CachedBrandProductPage.ProductSummary.builder()
                                    .productId(1L)
                                    .productName("상품A")
                                    .basePrice(Money.of(10000L))
                                    .deleted(false)
                                    .likeCount(5L)
                                    .build(),
                            CachedBrandProductPage.ProductSummary.builder()
                                    .productId(2L)
                                    .productName("상품B")
                                    .basePrice(Money.of(20000L))
                                    .deleted(false)
                                    .likeCount(10L)
                                    .build()))
                    .totalElements(2L)
                    .build();

            Brand brand = mock(Brand.class);
            given(brand.getId()).willReturn(brandId);
            given(brand.getName()).willReturn("테스트 브랜드");

            given(productAppService.getProductsByBrandIdCached(brandId, 0, 20)).willReturn(cachedPage);
            given(brandAppService.getById(brandId)).willReturn(brand);

            // when
            var result = productFacade.getProductsByBrand(brandId, 0, 20);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getProductName()).isEqualTo("상품A");
            assertThat(result.getContent().get(0).getBrandName()).isEqualTo("테스트 브랜드");
            assertThat(result.getTotalElements()).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("상품 목록 조회")
    class GetProductListTest {

        @Test
        @DisplayName("빈 상품 목록이면 빈 리스트를 반환한다")
        void getProductList_empty() {
            // given
            given(productAppService.getProducts(ProductSortCondition.LATEST)).willReturn(List.of());

            // when
            List<ProductInfo> result = productFacade.getProductList(ProductSortCondition.LATEST, null);

            // then
            assertThat(result).isEmpty();
            verify(brandAppService, never()).getByIds(any());
            verify(likeAppService, never()).countByProductIds(any());
        }

        @Test
        @DisplayName("상품 목록을 배치 조회로 조합하여 반환한다")
        void getProductList_withProducts() {
            // given
            Long productId1 = 1L;
            Long productId2 = 2L;
            Long brandId = 10L;
            Long userId = 100L;

            Product p1 = mock(Product.class);
            given(p1.getId()).willReturn(productId1);
            given(p1.getBrandId()).willReturn(brandId);
            given(p1.getName()).willReturn("상품A");
            given(p1.getBasePrice()).willReturn(Money.of(10000L));
            given(p1.isDeleted()).willReturn(false);

            Product p2 = mock(Product.class);
            given(p2.getId()).willReturn(productId2);
            given(p2.getBrandId()).willReturn(brandId);
            given(p2.getName()).willReturn("상품B");
            given(p2.getBasePrice()).willReturn(Money.of(20000L));
            given(p2.isDeleted()).willReturn(false);

            Brand brand = mock(Brand.class);
            given(brand.getId()).willReturn(brandId);
            given(brand.getName()).willReturn("테스트 브랜드");
            Map<Long, Brand> brandMap = Map.of(brandId, brand);

            Option opt1 = mock(Option.class);
            given(opt1.getId()).willReturn(1L);
            given(opt1.getName()).willReturn("옵션1");
            given(opt1.getAdditionalPrice()).willReturn(Money.of(0L));
            given(opt1.getStock()).willReturn(10);
            given(opt1.isSoldOut()).willReturn(false);

            Option opt2 = mock(Option.class);
            given(opt2.getId()).willReturn(2L);
            given(opt2.getName()).willReturn("옵션2");
            given(opt2.getAdditionalPrice()).willReturn(Money.of(500L));
            given(opt2.getStock()).willReturn(20);
            given(opt2.isSoldOut()).willReturn(false);

            Map<Long, List<Option>> optionMap = Map.of(
                    productId1, List.of(opt1),
                    productId2, List.of(opt2)
            );
            Map<Long, Long> likeCountMap = Map.of(productId1, 5L, productId2, 10L);
            Set<Long> likedProductIds = Set.of(productId1);

            given(productAppService.getProducts(ProductSortCondition.LATEST)).willReturn(List.of(p1, p2));
            given(brandAppService.getByIds(List.of(brandId))).willReturn(brandMap);
            given(productAppService.getOptionsByProductIds(List.of(productId1, productId2))).willReturn(optionMap);
            given(likeAppService.countByProductIds(List.of(productId1, productId2))).willReturn(likeCountMap);
            given(likeAppService.getLikedProductIds(userId, List.of(productId1, productId2))).willReturn(likedProductIds);

            // when
            List<ProductInfo> result = productFacade.getProductList(ProductSortCondition.LATEST, userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getLikeCount()).isEqualTo(5L);
            assertThat(result.get(0).isLikedByUser()).isTrue();
            assertThat(result.get(1).getLikeCount()).isEqualTo(10L);
            assertThat(result.get(1).isLikedByUser()).isFalse();
        }

        @Test
        @DisplayName("비로그인 사용자의 경우 likedByUser가 모두 false로 반환된다")
        void getProductList_noUser() {
            // given
            Long productId = 1L;
            Long brandId = 10L;

            Product product = mock(Product.class);
            given(product.getId()).willReturn(productId);
            given(product.getBrandId()).willReturn(brandId);
            given(product.getName()).willReturn("상품");
            given(product.getBasePrice()).willReturn(Money.of(10000L));
            given(product.isDeleted()).willReturn(false);

            Brand brand = mock(Brand.class);
            given(brand.getId()).willReturn(brandId);
            given(brand.getName()).willReturn("브랜드");

            given(productAppService.getProducts(ProductSortCondition.LATEST)).willReturn(List.of(product));
            given(brandAppService.getByIds(List.of(brandId))).willReturn(Map.of(brandId, brand));
            given(productAppService.getOptionsByProductIds(List.of(productId))).willReturn(Map.of());
            given(likeAppService.countByProductIds(List.of(productId))).willReturn(Map.of());

            // when
            List<ProductInfo> result = productFacade.getProductList(ProductSortCondition.LATEST, null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).isLikedByUser()).isFalse();
            verify(likeAppService, never()).getLikedProductIds(any(), any());
        }
    }
}
