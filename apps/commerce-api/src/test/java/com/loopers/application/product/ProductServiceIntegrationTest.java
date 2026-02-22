package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.application.brand.BrandService;
import com.loopers.application.like.LikeService;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.like.persistence.LikeJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageSize;
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
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

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

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {

        @DisplayName("브랜드 ID에 해당하는 상품들이 반환된다.")
        @Test
        void returnsProductsByBrandId() {
            // arrange
            var brandResult = brandService.createBrand("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            var command1 = new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품명1", "https://example.com/thumb1.png", 10000L, 100L, "상품 설명1"
            );
            var command2 = new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품명2", "https://example.com/thumb2.png", 20000L, 200L, "상품 설명2"
            );
            productService.createProduct(command1);
            productService.createProduct(command2);

            // act
            var products = productService.getProductsByBrandId(brandResult.id(), new PageSize(0, 10));

            // assert
            assertThat(products.content()).hasSize(2)
                    .extracting("name")
                    .containsExactlyInAnyOrder("상품명1", "상품명2");
        }

        @DisplayName("brandId 없이 전체 상품을 조회할 수 있다.")
        @Test
        void returnsAllProducts_whenNoFilter() {
            // arrange
            var brand1 = brandService.createBrand("브랜드1", "https://example.com/logo1.png", "설명1");
            var brand2 = brandService.createBrand("브랜드2", "https://example.com/logo2.png", "설명2");
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brand1.id(), "상품1", "https://example.com/thumb1.png", 10000L, 100L, "설명"
            ));
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brand2.id(), "상품2", "https://example.com/thumb2.png", 20000L, 200L, "설명"
            ));

            // act
            var products = productService.getProducts(new PageSize(0, 10));

            // assert
            assertThat(products.content()).hasSize(2);
        }

        @DisplayName("다른 브랜드의 상품은 필터링 결과에 포함되지 않는다.")
        @Test
        void returnsProductsByBrandId_filteringOtherBrands() {
            // arrange
            var brand1 = brandService.createBrand("브랜드1", "https://example.com/logo1.png", "설명1");
            var brand2 = brandService.createBrand("브랜드2", "https://example.com/logo2.png", "설명2");
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brand1.id(), "상품1", "https://example.com/thumb1.png", 10000L, 100L, "설명"
            ));
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brand2.id(), "상품2", "https://example.com/thumb2.png", 20000L, 200L, "설명"
            ));

            // act
            var products = productService.getProductsByBrandId(brand1.id(), new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(products.content()).hasSize(1),
                    () -> assertThat(products.content().get(0).name()).isEqualTo("상품1")
            );
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsProductsSortedByCreatedAtDesc() {
            // arrange
            var brandResult = brandService.createBrand("브랜드명", "https://example.com/logo.png", "설명");
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "첫번째", "https://example.com/thumb1.png", 10000L, 100L, "설명"
            ));
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "두번째", "https://example.com/thumb2.png", 20000L, 200L, "설명"
            ));
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "세번째", "https://example.com/thumb3.png", 30000L, 300L, "설명"
            ));

            // act
            var products = productService.getProducts(new PageSize(0, 10));

            // assert
            assertThat(products.content())
                    .extracting("name")
                    .containsExactly("세번째", "두번째", "첫번째");
        }

        @DisplayName("상품이 없으면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoProducts() {
            // act
            var products = productService.getProducts(new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(products.content()).isEmpty(),
                    () -> assertThat(products.hasNext()).isFalse()
            );
        }

        @DisplayName("페이지 크기보다 상품이 많으면, hasNext가 true이다.")
        @Test
        void returnsHasNextTrue_whenMoreProductsExist() {
            // arrange
            var brandResult = brandService.createBrand("브랜드명", "https://example.com/logo.png", "설명");
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품1", "https://example.com/thumb1.png", 10000L, 100L, "설명"
            ));
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품2", "https://example.com/thumb2.png", 20000L, 200L, "설명"
            ));
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품3", "https://example.com/thumb3.png", 30000L, 300L, "설명"
            ));

            // act
            var products = productService.getProducts(new PageSize(0, 2));

            // assert
            assertAll(
                    () -> assertThat(products.content()).hasSize(2),
                    () -> assertThat(products.hasNext()).isTrue()
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 조회하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandNotFound() {
            // act & assert
            assertThatThrownBy(() -> productService.getProductsByBrandId(999L, new PageSize(0, 10)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("상품을 상세 조회할 때,")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품이면, 상품 정보가 반환된다.")
        @Test
        void returnsProductResult_whenProductExists() {
            // arrange
            var productId = createProduct();

            // act
            var result = productService.getProduct(productId);

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(productId),
                    () -> assertThat(result.name()).isEqualTo("상품명"),
                    () -> assertThat(result.thumbnailUrl()).isEqualTo("https://example.com/thumb.png"),
                    () -> assertThat(result.price()).isEqualTo(10000L),
                    () -> assertThat(result.stock()).isEqualTo(100L),
                    () -> assertThat(result.description()).isEqualTo("상품 설명")
            );
        }

        @DisplayName("삭제된 상품이면, 삭제된 상품 정보가 반환된다.")
        @Test
        void returnsProductResult_whenProductIsDeleted() {
            // arrange
            var productId = createProduct();
            productService.deleteProduct(productId);

            // act
            var result = productService.getProduct(productId);

            // assert
            assertAll(
                    () -> assertThat(result.id()).isEqualTo(productId),
                    () -> assertThat(result.deletedAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // act & assert
            assertThatThrownBy(() -> productService.getProduct(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("상품 정보를 수정할 때,")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 정보를 입력하면, 상품 정보가 수정된다.")
        @Test
        void updatesProduct_whenValidInputProvided() {
            // arrange
            var productId = createProduct();
            var command = new ProductCommand.UpdateProductCommand(
                    productId,
                    "수정된 상품명",
                    "https://example.com/new-thumb.png",
                    20000L,
                    200L,
                    "수정된 설명"
            );

            // act
            productService.updateProduct(command);

            // assert
            var updatedProduct = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(updatedProduct.getName().getValue()).isEqualTo("수정된 상품명"),
                    () -> assertThat(updatedProduct.getThumbnailUrl().getValue()).isEqualTo("https://example.com/new-thumb.png"),
                    () -> assertThat(updatedProduct.getPrice().getAmount()).isEqualTo(20000L),
                    () -> assertThat(updatedProduct.getStock().getValue()).isEqualTo(200L),
                    () -> assertThat(updatedProduct.getDescription()).isEqualTo("수정된 설명"),
                    () -> assertThat(updatedProduct.getBrandId()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            var command = new ProductCommand.UpdateProductCommand(
                    999L,
                    "상품명",
                    "https://example.com/thumb.png",
                    10000L,
                    100L,
                    "설명"
            );

            // act & assert
            assertThatThrownBy(() -> productService.updateProduct(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("삭제된 상품이면, ALREADY_DELETED_PRODUCT 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createProduct();
            productService.deleteProduct(productId);
            var command = new ProductCommand.UpdateProductCommand(
                    productId,
                    "수정된 상품명",
                    "https://example.com/new-thumb.png",
                    20000L,
                    200L,
                    "수정된 설명"
            );

            // act & assert
            assertThatThrownBy(() -> productService.updateProduct(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_DELETED_PRODUCT));
        }

    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @DisplayName("유효한 상품이면, 상품이 삭제되고 좋아요도 함께 삭제된다.")
        @Test
        void deletesProductAndAssociatedLikes_whenProductExists() {
            // arrange
            var productId = createProduct();
            var userId = 1L;
            likeService.likeProduct(userId, productId);

            // act
            productService.deleteProduct(productId);

            // assert
            assertAll(
                    () -> assertThat(productRepository.findById(productId).orElseThrow().getDeletedAt()).isNotNull(),
                    () -> assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isFalse()
            );
        }

        @DisplayName("이미 삭제된 상품이면, 아무 동작 없이 성공한다.")
        @Test
        void succeedsIdempotently_whenProductIsAlreadyDeleted() {
            // arrange
            var productId = createProduct();
            productService.deleteProduct(productId);

            // act & assert
            assertThatCode(() -> productService.deleteProduct(productId))
                    .doesNotThrowAnyException();
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // act & assert
            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

    }

    private Long createProduct() {
        var brandResult = brandService.createBrand("브랜드명", "https://example.com/logo.png", "브랜드 설명");
        var command = new ProductCommand.CreateProductCommand(
                brandResult.id(), "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"
        );
        return productService.createProduct(command);
    }
}
