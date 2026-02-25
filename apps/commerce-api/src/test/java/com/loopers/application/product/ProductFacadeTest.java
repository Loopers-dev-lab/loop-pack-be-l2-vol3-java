package com.loopers.application.product;

import com.loopers.application.brand.BrandAppService;
import com.loopers.application.like.LikeAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductSortCondition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
        productAppService = mock(ProductAppService.class);
        brandAppService = mock(BrandAppService.class);
        likeAppService = mock(LikeAppService.class);
        productFacade = new ProductFacade(productAppService, brandAppService, likeAppService);
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

            Product product = Product.of(productId, brandId, "테스트 상품", Money.of(10000L), false);
            Brand brand = Brand.of(brandId, "테스트 브랜드", false);
            List<Option> options = List.of(
                    Option.of(1L, productId, "기본", Money.of(0L), 50, false)
            );

            given(productAppService.getById(productId)).willReturn(product);
            given(brandAppService.getById(brandId)).willReturn(brand);
            given(productAppService.getOptionsByProductId(productId)).willReturn(options);
            given(likeAppService.countByProductId(productId)).willReturn(42L);
            given(likeAppService.isLikedByUser(userId, productId)).willReturn(true);

            // when
            ProductInfo result = productFacade.getProductDetail(productId, userId);

            // then
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getBrandName()).isEqualTo("테스트 브랜드");
            assertThat(result.getLikeCount()).isEqualTo(42L);
            assertThat(result.isLikedByUser()).isTrue();
            assertThat(result.getOptions()).hasSize(1);
        }

        @Test
        @DisplayName("비로그인 사용자는 likedByUser가 false로 반환된다")
        void getProductDetail_noUser() {
            // given
            Long productId = 1L;
            Long brandId = 10L;

            Product product = Product.of(productId, brandId, "테스트 상품", Money.of(10000L), false);
            Brand brand = Brand.of(brandId, "테스트 브랜드", false);

            given(productAppService.getById(productId)).willReturn(product);
            given(brandAppService.getById(brandId)).willReturn(brand);
            given(productAppService.getOptionsByProductId(productId)).willReturn(List.of());
            given(likeAppService.countByProductId(productId)).willReturn(0L);

            // when
            ProductInfo result = productFacade.getProductDetail(productId, null);

            // then
            assertThat(result.isLikedByUser()).isFalse();
            verify(likeAppService, never()).isLikedByUser(any(), any());
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

            List<Product> products = List.of(
                    Product.of(productId1, brandId, "상품A", Money.of(10000L), false),
                    Product.of(productId2, brandId, "상품B", Money.of(20000L), false)
            );

            Brand brand = Brand.of(brandId, "테스트 브랜드", false);
            Map<Long, Brand> brandMap = Map.of(brandId, brand);
            Map<Long, List<Option>> optionMap = Map.of(
                    productId1, List.of(Option.of(1L, productId1, "옵션1", Money.of(0L), 10, false)),
                    productId2, List.of(Option.of(2L, productId2, "옵션2", Money.of(500L), 20, false))
            );
            Map<Long, Long> likeCountMap = Map.of(productId1, 5L, productId2, 10L);
            Set<Long> likedProductIds = Set.of(productId1);

            given(productAppService.getProducts(ProductSortCondition.LATEST)).willReturn(products);
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

            List<Product> products = List.of(
                    Product.of(productId, brandId, "상품", Money.of(10000L), false)
            );
            Brand brand = Brand.of(brandId, "브랜드", false);

            given(productAppService.getProducts(ProductSortCondition.LATEST)).willReturn(products);
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
