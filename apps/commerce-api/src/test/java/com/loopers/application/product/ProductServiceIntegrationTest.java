package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.application.brand.BrandService;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 정보를 입력하면, 상품이 DB에 저장되고 상품 ID가 반환된다.")
        @Test
        void savesProductToDatabase_whenValidInputProvided() {
            // arrange
            var brandResult = brandService.createBrand("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            var command = new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"
            );

            // act
            var productId = productService.createProduct(command);

            // assert
            var savedProduct = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(savedProduct.getId()).isEqualTo(productId),
                    () -> assertThat(savedProduct.getBrandId()).isEqualTo(brandResult.id()),
                    () -> assertThat(savedProduct.getName().getValue()).isEqualTo("상품명"),
                    () -> assertThat(savedProduct.getThumbnailUrl().getValue()).isEqualTo("https://example.com/thumb.png"),
                    () -> assertThat(savedProduct.getPrice().getAmount()).isEqualTo(10000L),
                    () -> assertThat(savedProduct.getStock().getValue()).isEqualTo(100L),
                    () -> assertThat(savedProduct.getDescription()).isEqualTo("상품 설명")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // arrange
            var command = new ProductCommand.CreateProductCommand(
                    999L, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"
            );

            // act & assert
            assertThatThrownBy(() -> productService.createProduct(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("삭제된 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandIsDeleted() {
            // arrange
            var brandResult = brandService.createBrand("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            var brand = brandRepository.findById(brandResult.id()).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            var command = new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"
            );

            // act & assert
            assertThatThrownBy(() -> productService.createProduct(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }
}