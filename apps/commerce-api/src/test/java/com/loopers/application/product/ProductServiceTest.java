package com.loopers.application.product;

import com.loopers.application.product.CreateProductUseCase.ProductCreateCommand;
import com.loopers.application.product.UpdateProductUseCase.ProductUpdateCommand;
import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.brand.BrandData;
import com.loopers.domain.model.brand.BrandName;
import com.loopers.domain.model.product.*;
import com.loopers.domain.repository.BrandRepository;
import com.loopers.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTest {

    private ProductRepository productRepository;
    private BrandRepository brandRepository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        brandRepository = mock(BrandRepository.class);
        service = new ProductService(productRepository, brandRepository);
    }

    @Nested
    @DisplayName("상품 생성")
    class CreateProduct {

        @Test
        @DisplayName("상품 생성 성공")
        void createProduct_success() {
            // given
            Brand brand = createBrand(1L);
            when(brandRepository.findActiveById(1L)).thenReturn(Optional.of(brand));

            // when & then
            var command = new ProductCreateCommand(1L, "운동화", 50000, null, 100, "좋은 운동화");
            assertThatNoException()
                    .isThrownBy(() -> service.createProduct(command));

            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("존재하지 않는 브랜드로 생성시 예외")
        void createProduct_fail_brandNotFound() {
            // given
            when(brandRepository.findActiveById(999L)).thenReturn(Optional.empty());

            // when & then
            var command = new ProductCreateCommand(999L, "운동화", 50000, null, 100, "좋은 운동화");
            assertThatThrownBy(() -> service.createProduct(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("존재하지 않는 브랜드");

            verify(productRepository, never()).save(any(Product.class));
        }

        @Test
        @DisplayName("삭제된 브랜드로 생성시 예외")
        void createProduct_fail_deletedBrand() {
            // given
            when(brandRepository.findActiveById(1L)).thenReturn(Optional.empty());

            // when & then
            var command = new ProductCreateCommand(1L, "운동화", 50000, null, 100, "좋은 운동화");
            assertThatThrownBy(() -> service.createProduct(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("존재하지 않는 브랜드");
        }
    }

    @Nested
    @DisplayName("상품 수정")
    class UpdateProduct {

        @Test
        @DisplayName("상품 수정 성공")
        void updateProduct_success() {
            // given
            Product product = createProduct(1L, 1L);
            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));

            // when & then
            var command = new ProductUpdateCommand(1L, "새 이름", 60000, null, 200, "변경된 설명");
            assertThatNoException()
                    .isThrownBy(() -> service.updateProduct(command));

            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("존재하지 않는 상품 수정시 예외")
        void updateProduct_fail_notFound() {
            // given
            when(productRepository.findActiveById(999L)).thenReturn(Optional.empty());

            // when & then
            var command = new ProductUpdateCommand(999L, "새 이름", 60000, null, 200, "설명");
            assertThatThrownBy(() -> service.updateProduct(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("상품 삭제")
    class DeleteProduct {

        @Test
        @DisplayName("상품 삭제 성공")
        void deleteProduct_success() {
            // given
            Product product = createProduct(1L, 1L);
            when(productRepository.findActiveById(1L)).thenReturn(Optional.of(product));

            // when
            service.deleteProduct(1L);

            // then
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("존재하지 않는 상품 삭제시 예외")
        void deleteProduct_fail_notFound() {
            // given
            when(productRepository.findActiveById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.deleteProduct(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }
    }

    private Brand createBrand(Long id) {
        return Brand.reconstitute(new BrandData(id, BrandName.of("나이키"), "스포츠 브랜드",
                LocalDateTime.now(), LocalDateTime.now(), null));
    }

    private Product createProduct(Long id, Long brandId) {
        return Product.reconstitute(new ProductData(id, brandId, ProductName.of("운동화"), Price.of(50000),
                null, Stock.of(100), 0, "좋은 운동화",
                LocalDateTime.now(), LocalDateTime.now(), null));
    }
}
