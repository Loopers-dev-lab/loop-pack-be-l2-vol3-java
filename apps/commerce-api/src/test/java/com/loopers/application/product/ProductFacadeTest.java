package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.loopers.application.product.dto.ProductResult;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ImageType;
import com.loopers.domain.product.ProductImageModel;
import com.loopers.domain.product.ProductImageService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("ProductFacade 단위 테스트")
@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private ProductImageService productImageService;

    @InjectMocks
    private ProductFacade productFacade;

    @DisplayName("상품 상세를 조회할 때, ")
    @Nested
    class GetProduct {

        @DisplayName("좋아요 수가 포함된 ProductResult를 반환한다.")
        @Test
        void getProduct_returnsResultWithLikeCount() {
            // arrange
            ProductModel product = ProductModel.create(1L, "에어맥스", 150000, 100);
            for (int i = 0; i < 5; i++) {
                product.addLikeCount();
            }
            when(productService.getById(1L)).thenReturn(product);
            when(brandService.getById(1L)).thenReturn(BrandModel.create("나이키"));

            // act
            ProductResult result = productFacade.getProduct(1L);

            // assert
            assertThat(result.likeCount()).isEqualTo(5L);
        }
    }

    @DisplayName("상품 상세(이미지 포함)를 조회할 때, ")
    @Nested
    class GetProductDetail {

        @DisplayName("메인 이미지와 디테일 이미지를 분리하여 반환한다.")
        @Test
        void getProductDetail_returnsDetailWithImages() {
            // arrange
            ProductModel product = ProductModel.create(1L, "에어맥스", 150000, 100);
            when(productService.getById(1L)).thenReturn(product);
            when(brandService.getById(1L)).thenReturn(BrandModel.create("나이키"));
            when(productImageService.getImagesByProductIdAndType(1L, ImageType.MAIN))
                    .thenReturn(List.of(
                            ProductImageModel.create(1L, "https://img.com/main1.jpg", ImageType.MAIN, 0),
                            ProductImageModel.create(1L, "https://img.com/main2.jpg", ImageType.MAIN, 1)));
            when(productImageService.getImagesByProductIdAndType(1L, ImageType.DETAIL))
                    .thenReturn(List.of(
                            ProductImageModel.create(1L, "https://img.com/detail1.jpg", ImageType.DETAIL, 0)));

            // act
            ProductResult.DetailWithImages result = productFacade.getProductDetail(1L);

            // assert
            assertThat(result.product().name()).isEqualTo("에어맥스");
            assertThat(result.product().brandName()).isEqualTo("나이키");
            assertThat(result.mainImages()).hasSize(2);
            assertThat(result.detailImages()).hasSize(1);
            assertThat(result.mainImages().get(0).imageUrl()).isEqualTo("https://img.com/main1.jpg");
            assertThat(result.detailImages().get(0).imageType()).isEqualTo(ImageType.DETAIL);
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetProductsWithActiveBrand {

        @DisplayName("각 상품에 좋아요 수가 포함된다.")
        @Test
        void getProductsWithActiveBrand_returnsResultsWithLikeCount() {
            // arrange
            ProductModel product1 = ProductModel.create(1L, "에어맥스", 150000, 100);
            for (int i = 0; i < 3; i++) {
                product1.addLikeCount();
            }
            ProductModel product2 = ProductModel.create(1L, "에어포스", 120000, 50);
            PageRequest pageable = PageRequest.of(0, 20);

            when(productService.getAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(product1, product2), pageable, 2));
            when(brandService.getActiveNameMapByIds(List.of(1L)))
                    .thenReturn(Map.of(1L, "나이키"));

            // act
            Page<ProductResult> result = productFacade.getProductsWithActiveBrand(pageable);

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).likeCount()).isEqualTo(3L);
        }
    }

    @DisplayName("좋아요 수 정렬로 상품 목록을 조회할 때, ")
    @Nested
    class GetProductsWithActiveBrandSortedByLikes {

        @DisplayName("좋아요 수 내림차순으로 DB 페이지네이션된 결과를 반환한다.")
        @Test
        void getProductsSortedByLikes_returnsSortedAndPaginated() {
            // arrange
            ProductModel product1 = ProductModel.create(1L, "에어맥스", 150000, 100);
            ProductModel product2 = ProductModel.create(1L, "에어포스", 120000, 50);
            PageRequest pageable = PageRequest.of(0, 2);

            when(productService.getAllSortedByLikeCountDesc(pageable))
                    .thenReturn(new PageImpl<>(List.of(product1, product2), pageable, 3));
            when(brandService.getActiveNameMapByIds(List.of(1L)))
                    .thenReturn(Map.of(1L, "나이키"));

            // act
            Page<ProductResult> result =
                    productFacade.getProductsWithActiveBrandSortedByLikes(0, 2);

            // assert
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }
    }

    @DisplayName("브랜드ID로 활성 브랜드 상품 목록을 조회할 때, ")
    @Nested
    class GetProductsWithActiveBrandByBrandId {

        @DisplayName("비활성 브랜드이면 빈 결과를 반환한다.")
        @Test
        void getProductsWithActiveBrandByBrandId_inactiveBrand_returnsEmpty() {
            // arrange
            ProductModel product = ProductModel.create(1L, "에어맥스", 150000, 100);
            PageRequest pageable = PageRequest.of(0, 20);

            when(productService.getAllByBrandId(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
            when(brandService.getActiveNameMapByIds(List.of(1L)))
                    .thenReturn(Map.of());

            // act
            Page<ProductResult> result =
                    productFacade.getProductsWithActiveBrandByBrandId(1L, pageable);

            // assert
            assertThat(result.getContent()).isEmpty();
        }

        @DisplayName("활성 브랜드이면 좋아요 수가 포함된 결과를 반환한다.")
        @Test
        void getProductsWithActiveBrandByBrandId_activeBrand_returnsResults() {
            // arrange
            ProductModel product = ProductModel.create(1L, "에어맥스", 150000, 100);
            for (int i = 0; i < 10; i++) {
                product.addLikeCount();
            }
            PageRequest pageable = PageRequest.of(0, 20);

            when(productService.getAllByBrandId(1L, pageable))
                    .thenReturn(new PageImpl<>(List.of(product), pageable, 1));
            when(brandService.getActiveNameMapByIds(List.of(1L)))
                    .thenReturn(Map.of(1L, "나이키"));

            // act
            Page<ProductResult> result =
                    productFacade.getProductsWithActiveBrandByBrandId(1L, pageable);

            // assert
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).brandName()).isEqualTo("나이키");
            assertThat(result.getContent().get(0).likeCount()).isEqualTo(10L);
        }
    }
}
