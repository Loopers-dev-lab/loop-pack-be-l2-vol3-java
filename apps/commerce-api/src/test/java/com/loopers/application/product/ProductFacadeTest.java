package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.page.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductFacadeTest {

    BrandRepository brandRepository = mock(BrandRepository.class);
    ProductRepository productRepository = mock(ProductRepository.class);
    LikeRepository likeRepository = mock(LikeRepository.class);
    ProductAssembler productAssembler = new ProductAssembler();
    ProductQueryService productQueryService = mock(ProductQueryService.class);
    ProductFacade productFacade = new ProductFacade(brandRepository, productRepository, likeRepository, productAssembler, productQueryService);

    @DisplayName("상품 등록 시, ")
    @Nested
    class Register {

        @DisplayName("존재하는 브랜드이면, 상품을 저장한다.")
        @Test
        void savesProduct_whenBrandExists() {
            // arrange
            Long brandId = 1L;
            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));

            // act
            productFacade.register("나이키 에어맥스", "설명", 10, 150000, brandId);

            // assert
            verify(productRepository).save(any());
        }

        @DisplayName("존재하지 않는 브랜드이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenBrandNotFound() {
            // arrange
            Long brandId = 999L;
            when(brandRepository.findById(brandId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productFacade.register("나이키 에어맥스", "설명", 10, 150000, brandId)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("이미 등록된 브랜드로만 상품을 등록할 수 있습니다.");
        }
    }

    @DisplayName("상품 목록 조회 시, ")
    @Nested
    class GetList {

        @DisplayName("브랜드가 존재하는 상품이면, 브랜드명과 좋아요 수를 포함한 상품 정보를 반환한다.")
        @Test
        void returnsProductInfoWithBrandNameAndLikeCount_whenBrandExists() {
            // arrange
            Long brandId = 1L;
            Brand brand = mock(Brand.class);
            when(brand.getId()).thenReturn(brandId);
            when(brand.name()).thenReturn("나이키");

            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(150000), brandId);

            PageRequest pageRequest = PageRequest.of(0, 20);
            when(productRepository.findAll(pageRequest)).thenReturn(new PageResponse<>(List.of(product), 0, 20, 1));
            when(brandRepository.findAllByIdIn(List.of(brandId))).thenReturn(List.of(brand));

            // act
            PageResponse<ProductInfo> result = productFacade.getList(pageRequest);

            // assert
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).brand()).isEqualTo("나이키");
            assertThat(result.content().get(0).likeCount()).isEqualTo(0L);
        }

        @DisplayName("브랜드가 존재하지 않는 상품이면, 브랜드명이 null인 상품 정보를 반환한다.")
        @Test
        void returnsProductInfoWithNullBrand_whenBrandNotFound() {
            // arrange
            Long brandId = 999L;
            Product product = Product.of("나이키 에어맥스", "설명", Stock.from(10), Price.from(150000), brandId);

            PageRequest pageRequest = PageRequest.of(0, 20);
            when(productRepository.findAll(pageRequest)).thenReturn(new PageResponse<>(List.of(product), 0, 20, 1));
            when(brandRepository.findAllByIdIn(List.of(brandId))).thenReturn(List.of());

            // act
            PageResponse<ProductInfo> result = productFacade.getList(pageRequest);

            // assert
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).brand()).isNull();
        }

        @DisplayName("등록된 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoProducts() {
            // arrange
            PageRequest pageRequest = PageRequest.of(0, 20);
            when(productRepository.findAll(pageRequest)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0));

            // act
            PageResponse<ProductInfo> result = productFacade.getList(pageRequest);

            // assert
            assertThat(result.content()).isEmpty();
        }

        @DisplayName("브랜드 필터 + 정렬 조회 시, ProductQueryService 에 위임한다.")
        @Test
        void delegatesToProductQueryService_whenBrandIdAndSortGiven() {
            // arrange
            Long brandId = 1L;
            PageRequest pageable = PageRequest.of(0, 20);
            PageResponse<ProductInfo> expected = new PageResponse<>(List.of(), 0, 20, 0);
            when(productQueryService.getList(pageable, brandId, "latest")).thenReturn(expected);

            // act
            PageResponse<ProductInfo> result = productFacade.getList(pageable, brandId, "latest");

            // assert
            verify(productQueryService).getList(eq(pageable), eq(brandId), eq("latest"));
            assertThat(result).isEqualTo(expected);
        }
    }

    @DisplayName("상품 상세 조회 시, ")
    @Nested
    class GetDetail {

        @DisplayName("존재하는 상품이면, ProductQueryService 에 위임한다.")
        @Test
        void delegatesToProductQueryService_whenProductExists() {
            // arrange
            Long productId = 1L;
            ProductInfo expected = new ProductInfo(productId, "나이키 에어맥스", "설명", 10, 150000, "나이키", 0L);
            when(productQueryService.getDetail(productId)).thenReturn(expected);

            // act
            ProductInfo result = productFacade.getDetail(productId);

            // assert
            verify(productQueryService).getDetail(productId);
            assertThat(result).isEqualTo(expected);
        }
    }

    @DisplayName("상품 수정 시, ")
    @Nested
    class Update {

        @DisplayName("존재하는 상품이면, 상품 정보를 수정한다.")
        @Test
        void updatesProduct_whenProductExists() {
            // arrange
            Long productId = 1L;
            Product product = mock(Product.class);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // act
            productFacade.update(productId, "나이키 줌", "새 설명", 20, 200000);

            // assert
            verify(product).update(eq("나이키 줌"), eq("새 설명"), eq(Stock.from(20)), eq(Price.from(200000)));
        }

        @DisplayName("존재하지 않는 상품이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenProductNotFound() {
            // arrange
            Long productId = 999L;
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                productFacade.update(productId, "나이키 줌", "새 설명", 20, 200000)
            );

            // assert
            assertThat(result.getCustomMessage()).isEqualTo("존재하지 않는 상품입니다.");
        }

        @DisplayName("상품 수정 시, ProductQueryService.evict 를 호출한다.")
        @Test
        void evictsCache_whenProductUpdated() {
            // arrange
            Long productId = 1L;
            Product product = mock(Product.class);
            when(productRepository.findById(productId)).thenReturn(Optional.of(product));

            // act
            productFacade.update(productId, "나이키 줌", "새 설명", 20, 200000);

            // assert
            verify(productQueryService).evict(productId);
        }
    }

    @DisplayName("상품 삭제 시, ")
    @Nested
    class Delete {

        @DisplayName("좋아요와 상품이 함께 삭제된다.")
        @Test
        void deletesLikesAndProduct() {
            // arrange
            Long productId = 1L;

            // act
            productFacade.delete(productId);

            // assert
            verify(likeRepository).deleteAllByProductId(productId);
            verify(productRepository).deleteById(productId);
        }
    }
}
