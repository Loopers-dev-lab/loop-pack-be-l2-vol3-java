package com.loopers.application.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.application.like.LikeService;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.PageSize;

class ProductServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 정보를 입력하면, 상품이 DB에 저장되고 상품 ID가 반환된다.")
        @Test
        void savesProductToDatabase_whenValidInputProvided() {
            // arrange
            var command = new ProductCommand.CreateProductCommand(
                    brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"
            );

            // act
            var productId = productService.createProduct(command);

            // assert
            var savedProduct = productService.getProduct(productId);
            assertAll(
                    () -> assertThat(savedProduct.id()).isEqualTo(productId),
                    () -> assertThat(savedProduct.brandId()).isEqualTo(brandId),
                    () -> assertThat(savedProduct.name()).isEqualTo("상품명"),
                    () -> assertThat(savedProduct.thumbnailUrl()).isEqualTo("https://example.com/thumb.png"),
                    () -> assertThat(savedProduct.price()).isEqualTo(10000L),
                    () -> assertThat(savedProduct.stock()).isEqualTo(100L),
                    () -> assertThat(savedProduct.description()).isEqualTo("상품 설명")
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
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }

        @DisplayName("삭제된 브랜드 ID를 입력하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandIsDeleted() {
            // arrange
            brandService.deleteBrand(brandId);

            var command = new ProductCommand.CreateProductCommand(
                    brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"
            );

            // act & assert
            assertThatThrownBy(() -> productService.createProduct(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {

        @DisplayName("브랜드 ID에 해당하는 상품들이 반환된다.")
        @Test
        void returnsProductsByBrandId() {
            // arrange
            var productId1 = createProduct(brandId, "상품 1", 10000L, 100L);
            var productId2 = createProduct(brandId, "상품 2", 20000L, 200L);

            // act
            var products = productService.getProductsByBrandId(brandId, new PageSize(0, 10));

            // assert
            assertThat(products.content()).hasSize(2)
                    .extracting("id")
                    .containsExactlyInAnyOrder(productId1, productId2);
        }

        @DisplayName("brandId 없이 전체 상품을 조회할 수 있다.")
        @Test
        void returnsAllProducts_whenNoFilter() {
            // arrange
            var otherBrandId = brandService.createBrand("브랜드 2", "https://example.com/logo2.png", "설명 2").id();
            createProduct(brandId, "상품 1", 10000L, 100L);
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    otherBrandId,
                    "상품 2",
                    "https://example.com/thumb2.png",
                    20000L,
                    200L,
                    "설명"
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
            var otherBrandId = brandService.createBrand("브랜드 2", "https://example.com/logo2.png", "설명 2").id();
            createProduct(brandId, "상품 1", 10000L, 100L);
            productService.createProduct(new ProductCommand.CreateProductCommand(
                    otherBrandId,
                    "상품 2",
                    "https://example.com/thumb2.png",
                    20000L,
                    200L,
                    "설명"
            ));

            // act
            var products = productService.getProductsByBrandId(brandId, new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(products.content()).hasSize(1),
                    () -> assertThat(products.content().get(0).name()).isEqualTo("상품 1")
            );
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsProductsSortedByCreatedAtDesc() {
            // arrange
            var productId1 = createProduct(brandId, "상품 1", 10000L, 100L);
            var productId2 = createProduct(brandId, "상품 2", 20000L, 200L);
            var productId3 = createProduct(brandId, "상품 3", 30000L, 300L);

            // act
            var products = productService.getProducts(new PageSize(0, 10));

            // assert
            assertThat(products.content())
                    .extracting("id")
                    .containsExactly(productId3, productId2, productId1);
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
            createProduct(brandId, "상품 1", 10000L, 100L);
            createProduct(brandId, "상품 2", 20000L, 200L);
            createProduct(brandId, "상품 3", 30000L, 300L);

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
            assertThatThrownBy(() -> productService.getProductsByBrandId(999L, new PageSize(0, 10)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("상품을 상세 조회할 때,")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품이면, 상품 정보가 반환된다.")
        @Test
        void returnsProductResult_whenProductExists() {
            // arrange
            var productId = createProduct(brandId);

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
            var productId = createProduct(brandId);
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
            assertThatThrownBy(() -> productService.getProduct(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("활성 상품을 상세 조회할 때,")
    @Nested
    class GetActiveProduct {

        @DisplayName("존재하는 활성 상품이면, 브랜드 정보와 좋아요 수를 포함한 상세 정보가 반환된다.")
        @Test
        void returnsProductDetail_whenActiveProductExists() {
            // arrange
            var productId = createProduct(brandId);

            // act
            var result = productService.getActiveProduct(null, productId);

            // assert
            assertAll(
                    () -> assertThat(result.productId()).isEqualTo(productId),
                    () -> assertThat(result.name()).isEqualTo("상품명"),
                    () -> assertThat(result.thumbnailUrl()).isEqualTo("https://example.com/thumb.png"),
                    () -> assertThat(result.price()).isEqualTo(10000L),
                    () -> assertThat(result.stock()).isEqualTo(100L),
                    () -> assertThat(result.description()).isEqualTo("상품 설명"),
                    () -> assertThat(result.brandName()).isEqualTo("브랜드명"),
                    () -> assertThat(result.brandLogoUrl()).isEqualTo("https://example.com/logo.png"),
                    () -> assertThat(result.likeCount()).isZero(),
                    () -> assertThat(result.liked()).isFalse()
            );
        }

        @DisplayName("userId가 null이면, liked는 false를 반환한다.")
        @Test
        void returnsLikedFalse_whenUserIdIsNull() {
            // arrange
            var productId = createProduct(brandId);
            likeService.likeProduct(1L, productId);

            // act
            var result = productService.getActiveProduct(null, productId);

            // assert
            assertAll(
                    () -> assertThat(result.likeCount()).isEqualTo(1L),
                    () -> assertThat(result.liked()).isFalse()
            );
        }

        @DisplayName("userId가 주어지고 좋아요한 상품이면, liked는 true를 반환한다.")
        @Test
        void returnsLikedTrue_whenUserLikedProduct() {
            // arrange
            var userId = 1L;
            var productId = createProduct(brandId);
            likeService.likeProduct(userId, productId);

            // act
            var result = productService.getActiveProduct(userId, productId);

            // assert
            assertAll(
                    () -> assertThat(result.likeCount()).isEqualTo(1L),
                    () -> assertThat(result.liked()).isTrue()
            );
        }

        @DisplayName("삭제된 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createProduct(brandId);
            productService.deleteProduct(productId);

            // act & assert
            assertThatThrownBy(() -> productService.getActiveProduct(null, productId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            assertThatThrownBy(() -> productService.getActiveProduct(null, 999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("브랜드가 삭제된 상품이면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenBrandIsDeleted() {
            // arrange
            var productId = createProduct(brandId);
            var product = productRepository.findById(productId).orElseThrow();
            var brand = brandRepository.findById(product.getBrandId()).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            // act & assert
            assertThatThrownBy(() -> productService.getActiveProduct(null, productId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND));
        }
    }

    @DisplayName("상품 정보를 수정할 때,")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 정보를 입력하면, 상품 정보가 수정된다.")
        @Test
        void updatesProduct_whenValidInputProvided() {
            // arrange
            var productId = createProduct(brandId);
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
            var updatedProduct = productService.getProduct(productId);
            assertAll(
                    () -> assertThat(updatedProduct.name()).isEqualTo("수정된 상품명"),
                    () -> assertThat(updatedProduct.thumbnailUrl()).isEqualTo("https://example.com/new-thumb.png"),
                    () -> assertThat(updatedProduct.price()).isEqualTo(20000L),
                    () -> assertThat(updatedProduct.stock()).isEqualTo(200L),
                    () -> assertThat(updatedProduct.description()).isEqualTo("수정된 설명"),
                    () -> assertThat(updatedProduct.brandId()).isNotNull()
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
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("삭제된 상품이면, ALREADY_DELETED_PRODUCT 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createProduct(brandId);
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
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(
                            ErrorType.ALREADY_DELETED_PRODUCT));
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class DeleteProduct {

        @DisplayName("유효한 상품이면, 상품이 삭제되고 좋아요도 함께 삭제된다.")
        @Test
        void deletesProductAndAssociatedLikes_whenProductExists() {
            // arrange
            var productId = createProduct(brandId);
            var userId = 1L;
            likeService.likeProduct(userId, productId);

            // act
            productService.deleteProduct(productId);

            // assert
            assertAll(
                    () -> assertThat(productService.getProduct(productId).deletedAt()).isNotNull(),
                    () -> assertThat(likeService.getLikedProducts(userId, new PageSize(0, 20)).content()).isEmpty()
            );
        }

        @DisplayName("이미 삭제된 상품이면, 아무 동작 없이 성공한다.")
        @Test
        void succeedsIdempotently_whenProductIsAlreadyDeleted() {
            // arrange
            var productId = createProduct(brandId);
            productService.deleteProduct(productId);

            // act & assert
            assertThatCode(() -> productService.deleteProduct(productId))
                    .doesNotThrowAnyException();
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            assertThatThrownBy(() -> productService.deleteProduct(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(
                            e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }
}
