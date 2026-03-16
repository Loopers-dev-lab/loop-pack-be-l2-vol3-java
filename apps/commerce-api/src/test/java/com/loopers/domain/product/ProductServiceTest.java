package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandRepository brandRepository;

    private ProductService productService;

    private BrandModel brand;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, brandRepository);
        brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
    }

    @DisplayName("상품을 조회할 때, ")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 ID가 주어지면, 상품을 반환한다.")
        @Test
        void returnsProduct_whenIdExists() {
            // arrange
            Long productId = 1L;
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // act
            ProductModel result = productService.getProduct(productId);

            // assert
            assertAll(
                () -> assertThat(result.getName()).isEqualTo("에어맥스"),
                () -> assertThat(result.getPrice()).isEqualTo(150000L)
            );
        }

        @DisplayName("존재하지 않는 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIdDoesNotExist() {
            // arrange
            Long productId = 999L;
            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.getProduct(productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("기본 정렬로 조회하면, 페이징된 상품 목록을 반환한다.")
        @Test
        void returnsProductList_whenDefaultSort() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            List<ProductModel> products = List.of(
                new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE),
                new ProductModel(brand, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE)
            );
            Page<ProductModel> productPage = new PageImpl<>(products, pageable, products.size());
            given(productRepository.findAll(pageable, null)).willReturn(productPage);

            // act
            Page<ProductModel> result = productService.getAll(pageable, ProductSortType.LATEST, null);

            // assert
            assertThat(result.getContent()).hasSize(2);
        }

        @DisplayName("LIKES_DESC 정렬로 조회하면, 좋아요 수 기준 정렬된 목록을 반환한다.")
        @Test
        void returnsProductList_whenSortByLikesDesc() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            List<ProductModel> products = List.of(
                new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE)
            );
            Page<ProductModel> productPage = new PageImpl<>(products, pageable, products.size());
            given(productRepository.findAllOrderByLikesDesc(pageable, null)).willReturn(productPage);

            // act
            Page<ProductModel> result = productService.getAll(pageable, ProductSortType.LIKES_DESC, null);

            // assert
            assertThat(result.getContent()).hasSize(1);
            verify(productRepository).findAllOrderByLikesDesc(pageable, null);
        }
    }

    @DisplayName("상품을 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("정상적인 정보가 주어지면, 상품이 저장된다.")
        @Test
        void savesProduct_whenValidInfoIsProvided() {
            // arrange
            Long brandId = 1L;
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));
            given(productRepository.save(any(ProductModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            ProductModel result = productService.register(brandId, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);

            // assert
            assertAll(
                () -> assertThat(result.getName()).isEqualTo("에어맥스"),
                () -> assertThat(result.getPrice()).isEqualTo(150000L),
                () -> assertThat(result.getBrand()).isEqualTo(brand)
            );
            verify(productRepository).save(any(ProductModel.class));
        }

        @DisplayName("존재하지 않는 브랜드 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandDoesNotExist() {
            // arrange
            Long brandId = 999L;
            given(brandRepository.findById(brandId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.register(brandId, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("정상적인 정보가 주어지면, 상품이 수정된다.")
        @Test
        void updatesProduct_whenValidInfoIsProvided() {
            // arrange
            Long productId = 1L;
            Long brandId = 1L;
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(brandRepository.findById(brandId)).willReturn(Optional.of(brand));

            // act
            ProductModel result = productService.update(productId, brandId, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE);

            // assert
            assertAll(
                () -> assertThat(result.getName()).isEqualTo("에어포스"),
                () -> assertThat(result.getPrice()).isEqualTo(120000L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // arrange
            Long productId = 999L;
            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.update(productId, 1L, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 브랜드 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenBrandDoesNotExist() {
            // arrange
            Long productId = 1L;
            Long brandId = 999L;
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));
            given(brandRepository.findById(brandId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.update(productId, brandId, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("상품을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 ID가 주어지면, 상품이 소프트 삭제된다.")
        @Test
        void deletesProduct_whenIdExists() {
            // arrange
            Long productId = 1L;
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            given(productRepository.findById(productId)).willReturn(Optional.of(product));

            // act
            productService.delete(productId);

            // assert
            assertThat(product.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenIdDoesNotExist() {
            // arrange
            Long productId = 999L;
            given(productRepository.findById(productId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.delete(productId);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("재고를 차감할 때, ")
    @Nested
    class DeductStock {

        @DisplayName("존재하는 상품의 재고를 차감하면, 비관적 락을 획득하고 재고가 줄어든다.")
        @Test
        void deductsStock_whenProductExists() {
            // arrange
            Long productId = 1L;
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            given(productRepository.findByIdForUpdate(productId)).willReturn(Optional.of(product));

            // act
            productService.deductStock(productId, 30);

            // assert
            assertThat(product.getStockQuantity()).isEqualTo(70);
            verify(productRepository).findByIdForUpdate(productId);
        }

        @DisplayName("존재하지 않는 상품 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenProductDoesNotExist() {
            // arrange
            Long productId = 999L;
            given(productRepository.findByIdForUpdate(productId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                productService.deductStock(productId, 30);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}
