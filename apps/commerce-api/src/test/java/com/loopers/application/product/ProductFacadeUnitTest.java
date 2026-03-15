package com.loopers.application.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.ProductLikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductCacheService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductFacadeUnitTest {

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private ProductLikeService productLikeService;

    @InjectMocks
    private ProductFacade productFacade;

    @Mock
    private ProductCacheService productCacheService;

    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품이면, Product + Brand + likeCount가 조합된다.")
        @Test
        void getByIdSuccess() {
            // given
            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "https://example.com/nike.png");
            ReflectionTestUtils.setField(product, "likeCount", 5);  // product.getLikeCount() 사용하므로 직접 세팅
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");

            when(productCacheService.getProductDetail(1L)).thenReturn(Optional.empty());
            when(productService.getById(1L)).thenReturn(product);
            when(brandService.getById(1L)).thenReturn(brand);

            // when
            ProductDetailInfo result = productFacade.getById(1L);

            // then
            assertAll(
                    () -> assertThat(result.name()).isEqualTo("에어맥스"),
                    () -> assertThat(result.brandName()).isEqualTo("나이키"),
                    () -> assertThat(result.likeCount()).isEqualTo(5L)
            );
            verify(productCacheService).setProductDetail(eq(1L), any(ProductDetailInfo.class));
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithNotFoundProduct() {
            // given
            when(productService.getById(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    productFacade.getById(999L)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("캐시에 존재하면, DB를 조회하지 않고 캐시에서 반환된다.")
        @Test
        void getByIdCacheHit() {
            // given
            ProductDetailInfo cached = new ProductDetailInfo(1L, "에어맥스", "러닝화", 129000, 100,
                    "https://example.com/nike.png", "나이키", 5L);
            when(productCacheService.getProductDetail(1L)).thenReturn(Optional.of(cached));

            // when
            ProductDetailInfo result = productFacade.getById(1L);

            // then
            assertThat(result.name()).isEqualTo("에어맥스");
            // DB 조회가 호출되지 않았는지 검증
            verify(productService, never()).getById(1L);
            verify(brandService, never()).getById(any());
        }
    }

    @DisplayName("상품 목록을 정렬 조회할 때,")
    @Nested
    class GetProductsWithSort {

        @DisplayName("PRICE_ASC로 정렬하면, 가격 오름차순으로 반환된다.")
        @Test
        void sortByPriceAsc() {
            // given
            ProductModel expensive = new ProductModel(1L, "에어맥스", "러닝화", 200000, 100, null);
            ReflectionTestUtils.setField(expensive, "id", 10L);
            ProductModel cheap = new ProductModel(1L, "에어포스", "캐주얼화", 100000, 50, null);
            ReflectionTestUtils.setField(cheap, "id", 11L);
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");
            ReflectionTestUtils.setField(brand, "id", 1L);

            when(productService.getAll(any(Sort.class))).thenReturn(List.of(cheap, expensive));
            when(brandService.getByIds(Set.of(1L))).thenReturn(Map.of(1L, brand));

            // when
            List<ProductDetailInfo> result = productFacade.getProducts(ProductSortType.PRICE_ASC);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).price())
                    .isLessThanOrEqualTo(result.get(1).price());
            verify(productService).getAll(ProductSortType.PRICE_ASC.toSort());
        }

        @DisplayName("LIKES_DESC로 정렬하면, 좋아요 수 내림차순으로 반환된다.")
        @Test
        void sortByLikesDesc() {
            // given
            ProductModel p1 = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);
            ReflectionTestUtils.setField(p1, "id", 10L);
            ProductModel p2 = new ProductModel(1L, "에어포스", "캐주얼화", 109000, 50, null);
            ReflectionTestUtils.setField(p2, "id", 11L);
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");
            ReflectionTestUtils.setField(brand, "id", 1L);

            when(productService.getAll(any(Sort.class))).thenReturn(List.of(p2, p1));
            when(brandService.getByIds(Set.of(1L))).thenReturn(Map.of(1L, brand));

            // when
            List<ProductDetailInfo> result = productFacade.getProducts(ProductSortType.LIKES_DESC);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).likeCount()).isGreaterThanOrEqualTo(result.get(1).likeCount());
            verify(productService).getAll(ProductSortType.LIKES_DESC.toSort());
        }
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class Register {

        @DisplayName("존재하는 브랜드이면, 상품이 등록되고 ProductInfo가 반환된다.")
        @Test
        void registerSuccess() {
            // given
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");
            ReflectionTestUtils.setField(brand, "id", 1L);

            ProductModel product = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, "https://example.com/airmax.png");
            ReflectionTestUtils.setField(product, "id", 10L);

            when(brandService.getById(1L)).thenReturn(brand);
            when(productService.register(1L, "에어맥스", "러닝화", 129000, 100, "https://example.com/airmax.png"))
                    .thenReturn(product);

            // when
            ProductInfo result = productFacade.register(1L, "에어맥스", "러닝화", 129000, 100, "https://example.com/airmax.png");

            // then
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(10L),
                    () -> assertThat(result.brandId()).isEqualTo(1L),
                    () -> assertThat(result.name()).isEqualTo("에어맥스"),
                    () -> assertThat(result.price()).isEqualTo(129000),
                    () -> assertThat(result.stockQuantity()).isEqualTo(100)
            );
            verify(brandService).getById(1L);
        }

        @DisplayName("존재하지 않는 브랜드이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void failWithBrandNotFound() {
            // given
            when(brandService.getById(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                    productFacade.register(999L, "에어맥스", "러닝화", 129000, 100, null)
            );

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("브랜드별 상품 목록을 조회할 때,")
    @Nested
    class GetProductsByBrandId {

        @DisplayName("해당 브랜드의 상품만 반환된다.")
        @Test
        void getProductsByBrandIdSuccess() {
            // given
            ProductModel p1 = new ProductModel(1L, "에어맥스", "러닝화", 129000, 100, null);
            ReflectionTestUtils.setField(p1, "id", 10L);
            ProductModel p2 = new ProductModel(1L, "에어포스", "캐주얼화", 109000, 50, null);
            ReflectionTestUtils.setField(p2, "id", 11L);
            BrandModel brand = new BrandModel("나이키", "스포츠 브랜드", "https://example.com/nike.png");
            ReflectionTestUtils.setField(brand, "id", 1L);

            when(productService.getByBrandId(eq(1L), any(Sort.class))).thenReturn(List.of(p1, p2));
            when(brandService.getById(1L)).thenReturn(brand);

            // when
            List<ProductDetailInfo> result = productFacade.getProductsByBrandId(1L, ProductSortType.LATEST);

            // then
            assertAll(
                    () -> assertThat(result).hasSize(2),
                    () -> assertThat(result.get(0).brandName()).isEqualTo("나이키"),
                    () -> assertThat(result.get(1).brandName()).isEqualTo("나이키")
            );
        }
    }
}
