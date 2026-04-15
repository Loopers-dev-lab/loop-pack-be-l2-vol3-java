package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.DiscountType;
import com.loopers.domain.product.ImageType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductImage;
import com.loopers.domain.product.ProductOption;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("ProductFacade 통합 테스트")
class ProductFacadeTest {

    @Autowired
    private ProductFacade productFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private ProductDetailCacheRepository productDetailCacheRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    private Brand savedBrand;
    private Category savedCategory;

    @BeforeEach
    void setUp() {
        savedBrand = brandRepository.save(new Brand("Apple", "애플", "https://example.com/apple.png"));
        savedCategory = categoryRepository.save(new Category("전자제품"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("getProduct")
    class GetProduct {

        @Test
        @DisplayName("상품 정보를 브랜드 정보와 함께 조회한다")
        void returnsProductInfoWithBrand() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L)
            );

            // Act
            ProductDetailWithRankingInfo result = productFacade.getProduct(product.getId());

            // Assert
            ProductDetailInfo detail = result.productDetail();
            assertAll(
                () -> assertThat(detail.id()).isEqualTo(product.getId()),
                () -> assertThat(detail.name()).isEqualTo("아이폰 15"),
                () -> assertThat(detail.brand()).isNotNull(),
                () -> assertThat(detail.brand().name()).isEqualTo("Apple"),
                () -> assertThat(detail.likeCount()).isEqualTo(0L),
                () -> assertThat(detail.options()).isEmpty(),
                () -> assertThat(detail.images()).isEmpty()
            );
        }

        @Test
        @DisplayName("상품 정보에 옵션과 이미지가 포함된다")
        void returnsProductInfoWithOptionsAndImages() {
            // Arrange
            List<ProductOption> options = List.of(
                new ProductOption(null, "256GB", "256GB", 0L, 100),
                new ProductOption(null, "512GB", "512GB", 100000L, 50)
            );
            List<ProductImage> images = List.of(
                new ProductImage(null, ImageType.MAIN, "https://example.com/main.jpg", "메인 이미지")
            );
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L, options, images)
            );

            // Act
            ProductDetailWithRankingInfo result = productFacade.getProduct(product.getId());

            // Assert
            ProductDetailInfo detail = result.productDetail();
            assertAll(
                () -> assertThat(detail.options()).hasSize(2),
                () -> assertThat(detail.options()).extracting(ProductOptionInfo::optionValue)
                    .containsExactlyInAnyOrder("256GB", "512GB"),
                () -> assertThat(detail.images()).hasSize(1),
                () -> assertThat(detail.images()).extracting(ProductImageInfo::type)
                    .containsExactly(ImageType.MAIN)
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품을 조회하면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenProductNotExists() {
            // Act & Assert
            assertThatThrownBy(() -> productFacade.getProduct(999L))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("상품 조회 후 Redis 캐시에 저장된다")
        void cachesProductDetailInfoInRedis() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L)
            );

            // Act
            productFacade.getProduct(product.getId());

            // Assert
            Optional<ProductDetailInfo> cached = productDetailCacheRepository.get(product.getId());
            assertThat(cached).isPresent();
            assertThat(cached.get().name()).isEqualTo("아이폰 15");
        }

        @Test
        @DisplayName("상품 조회 시 PRODUCT_VIEWED 이벤트가 outbox에 기록된다")
        void recordsProductViewedEvent() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L)
            );

            // Act
            productFacade.getProduct(product.getId());

            // Assert
            assertThat(outboxEventJpaRepository.findAll())
                .anyMatch(event -> "PRODUCT_VIEWED".equals(event.getEventType())
                    && String.valueOf(product.getId()).equals(event.getAggregateId()));
        }

        @Test
        @DisplayName("캐시 히트 시에도 PRODUCT_VIEWED 이벤트가 기록된다")
        void recordsProductViewedEvent_evenOnCacheHit() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L)
            );
            productFacade.getProduct(product.getId());
            long firstCount = outboxEventJpaRepository.findAll().stream()
                .filter(e -> "PRODUCT_VIEWED".equals(e.getEventType()))
                .count();

            // Act
            productFacade.getProduct(product.getId());

            // Assert
            long secondCount = outboxEventJpaRepository.findAll().stream()
                .filter(e -> "PRODUCT_VIEWED".equals(e.getEventType()))
                .count();
            assertThat(secondCount).isEqualTo(firstCount + 1);
        }

        @Test
        @DisplayName("캐시에 데이터가 있으면 캐시에서 반환한다")
        void returnsCachedData_whenCacheHit() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L)
            );
            productFacade.getProduct(product.getId());

            // Act - 두 번째 호출은 캐시에서 반환
            ProductDetailWithRankingInfo result = productFacade.getProduct(product.getId());

            // Assert
            assertThat(result.productDetail().name()).isEqualTo("아이폰 15");
        }
    }

    @Nested
    @DisplayName("getProducts")
    class GetProducts {

        @Test
        @DisplayName("페이지로 상품 목록을 조회하고 브랜드 정보를 포함한다")
        void returnsPagedProductsWithBrandInfo() {
            // Arrange
            productRepository.save(new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L));
            productRepository.save(new Product("아이폰 14", savedBrand.getId(), 1L, 1200000L));
            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<ProductInfo> result = productFacade.getProducts(null, null, null, ProductSortType.LATEST, pageable);

            // Assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(2),
                () -> assertThat(result.getTotalElements()).isEqualTo(2),
                () -> assertThat(result.getContent()).allMatch(info -> info.brand() != null),
                () -> assertThat(result.getContent()).allMatch(info -> info.brand().name().equals("Apple"))
            );
        }

        @Test
        @DisplayName("페이징 정보가 정상적으로 반환된다")
        void returnsCorrectPagingInfo() {
            // Arrange
            for (int i = 0; i < 25; i++) {
                productRepository.save(new Product("상품" + i, savedBrand.getId(), 1L, 1000000L + i));
            }
            Pageable pageable = PageRequest.of(0, 20);

            // Act
            Page<ProductInfo> result = productFacade.getProducts(null, null, null, ProductSortType.LATEST, pageable);

            // Assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(20),
                () -> assertThat(result.getTotalElements()).isEqualTo(25),
                () -> assertThat(result.getTotalPages()).isEqualTo(2),
                () -> assertThat(result.isFirst()).isTrue(),
                () -> assertThat(result.hasNext()).isTrue()
            );
        }

        @Test
        @DisplayName("브랜드 ID로 필터링하여 조회한다")
        void returnsFilteredProductsByBrandId() {
            // Arrange
            Brand samsung = brandRepository.save(new Brand("Samsung", "삼성", "https://example.com/samsung.png"));
            productRepository.save(new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L));
            productRepository.save(new Product("맥북 프로", savedBrand.getId(), 2L, 3000000L));
            productRepository.save(new Product("갤럭시 S24", samsung.getId(), 1L, 1400000L));
            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<ProductInfo> result = productFacade.getProducts(null, savedBrand.getId(), null, ProductSortType.LATEST, pageable);

            // Assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(2),
                () -> assertThat(result.getTotalElements()).isEqualTo(2),
                () -> assertThat(result.getContent()).allMatch(info -> info.brand().name().equals("Apple"))
            );
        }

        @Test
        @DisplayName("좋아요 많은순으로 정렬하여 조회한다")
        void returnsProducts_sortedByLikesDesc() {
            // Arrange
            Product product1 = productRepository.save(new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L));
            Product product2 = productRepository.save(new Product("갤럭시 S24", savedBrand.getId(), 1L, 1400000L));
            Product product3 = productRepository.save(new Product("맥북 프로", savedBrand.getId(), 2L, 3000000L));

            // 좋아요 수 설정: product2(5) > product1(3) > product3(1)
            product1.increaseLikeCount();
            product1.increaseLikeCount();
            product1.increaseLikeCount();
            productRepository.save(product1);

            product2.increaseLikeCount();
            product2.increaseLikeCount();
            product2.increaseLikeCount();
            product2.increaseLikeCount();
            product2.increaseLikeCount();
            productRepository.save(product2);

            product3.increaseLikeCount();
            productRepository.save(product3);

            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<ProductInfo> result = productFacade.getProducts(null, null, null, ProductSortType.LIKES_DESC, pageable);

            // Assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(3),
                () -> assertThat(result.getContent().get(0).id()).isEqualTo(product2.getId()),
                () -> assertThat(result.getContent().get(0).likeCount()).isEqualTo(5L),
                () -> assertThat(result.getContent().get(1).id()).isEqualTo(product1.getId()),
                () -> assertThat(result.getContent().get(1).likeCount()).isEqualTo(3L),
                () -> assertThat(result.getContent().get(2).id()).isEqualTo(product3.getId()),
                () -> assertThat(result.getContent().get(2).likeCount()).isEqualTo(1L)
            );
        }

        @Test
        @DisplayName("좋아요 수가 동일하면 최신순으로 정렬한다")
        void returnsProducts_sortedByCreatedAtDesc_whenLikeCountSame() {
            // Arrange
            Product product1 = productRepository.save(new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L));
            Product product2 = productRepository.save(new Product("갤럭시 S24", savedBrand.getId(), 1L, 1400000L));
            Product product3 = productRepository.save(new Product("맥북 프로", savedBrand.getId(), 2L, 3000000L));

            // 모든 상품 좋아요 수 동일하게 설정 (각 1개)
            product1.increaseLikeCount();
            productRepository.save(product1);

            product2.increaseLikeCount();
            productRepository.save(product2);

            product3.increaseLikeCount();
            productRepository.save(product3);

            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<ProductInfo> result = productFacade.getProducts(null, null, null, ProductSortType.LIKES_DESC, pageable);

            // Assert - 좋아요 수 동일하면 최신순 (product3 > product2 > product1)
            assertAll(
                () -> assertThat(result.getContent()).hasSize(3),
                () -> assertThat(result.getContent().get(0).id()).isEqualTo(product3.getId()),
                () -> assertThat(result.getContent().get(1).id()).isEqualTo(product2.getId()),
                () -> assertThat(result.getContent().get(2).id()).isEqualTo(product1.getId())
            );
        }
    }

    @Nested
    @DisplayName("createProduct (Admin)")
    class CreateProduct {

        @Test
        @DisplayName("관리자가 상품을 정상적으로 생성한다")
        void createsProduct() {
            // Arrange
            ProductCommand.Create command = new ProductCommand.Create(
                "아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L
            );

            // Act
            ProductAdminDetailInfo result = productFacade.createProduct("loopers.admin", command);

            // Assert
            assertAll(
                () -> assertThat(result.id()).isNotNull(),
                () -> assertThat(result.name()).isEqualTo("아이폰 15"),
                () -> assertThat(result.brandId()).isEqualTo(savedBrand.getId()),
                () -> assertThat(result.status()).isEqualTo(ProductStatus.SALE),
                () -> assertThat(result.options()).isEmpty(),
                () -> assertThat(result.images()).isEmpty()
            );
        }

        @Test
        @DisplayName("관리자가 아니면 FORBIDDEN 예외가 발생한다")
        void throwsForbidden_whenNotAdmin() {
            // Arrange
            ProductCommand.Create command = new ProductCommand.Create(
                "아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L
            );

            // Act & Assert
            assertThatThrownBy(() -> productFacade.createProduct("invalid.ldap", command))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }

        @Test
        @DisplayName("존재하지 않는 브랜드로 상품 생성 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenBrandNotExists() {
            // Arrange
            ProductCommand.Create command = new ProductCommand.Create(
                "아이폰 15", 99999L, savedCategory.getId(), 1500000L
            );

            // Act & Assert
            assertThatThrownBy(() -> productFacade.createProduct("loopers.admin", command))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("존재하지 않는 카테고리로 상품 생성 시 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenCategoryNotExists() {
            // Arrange
            ProductCommand.Create command = new ProductCommand.Create(
                "아이폰 15", savedBrand.getId(), 99999L, 1500000L
            );

            // Act & Assert
            assertThatThrownBy(() -> productFacade.createProduct("loopers.admin", command))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("deleteProduct (Admin)")
    class DeleteProduct {

        @Test
        @DisplayName("관리자가 상품을 삭제한다")
        void deletesProduct() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L)
            );

            // Act
            productFacade.deleteProduct("loopers.admin", product.getId());

            // Assert
            assertThatThrownBy(() -> productFacade.getProduct(product.getId()))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("상품 삭제 후 Redis 캐시가 삭제된다")
        void evictsCache_whenProductDeleted() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L)
            );
            productFacade.getProduct(product.getId()); // 캐시 저장
            assertThat(productDetailCacheRepository.get(product.getId())).isPresent();

            // Act
            productFacade.deleteProduct("loopers.admin", product.getId());

            // Assert
            assertThat(productDetailCacheRepository.get(product.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateProduct (Admin)")
    class UpdateProduct {

        @Test
        @DisplayName("관리자가 상품을 정상적으로 수정한다")
        void updatesProduct() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );
            ProductCommand.Update command = new ProductCommand.Update(
                "아이폰 15 Pro", savedCategory.getId(), 1800000L,
                100000L, DiscountType.PRICE, ProductStatus.SALE
            );

            // Act
            ProductAdminDetailInfo result = productFacade.updateProduct("loopers.admin", product.getId(), command);

            // Assert
            assertAll(
                () -> assertThat(result.name()).isEqualTo("아이폰 15 Pro"),
                () -> assertThat(result.basePrice()).isEqualTo(1800000L),
                () -> assertThat(result.discount()).isEqualTo(100000L),
                () -> assertThat(result.discountType()).isEqualTo(DiscountType.PRICE),
                () -> assertThat(result.discountedPrice()).isEqualTo(1700000L)
            );
        }

        @Test
        @DisplayName("잘못된 할인 정보로 수정 시 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenInvalidDiscount() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );
            ProductCommand.Update command = new ProductCommand.Update(
                "아이폰 15 Pro", savedCategory.getId(), 1000000L,
                1500000L, DiscountType.PRICE, ProductStatus.SALE // 할인이 가격보다 큼
            );

            // Act & Assert
            assertThatThrownBy(() -> productFacade.updateProduct("loopers.admin", product.getId(), command))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("할인 금액만 있고 할인 타입이 null이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenDiscountWithoutType() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );
            ProductCommand.Update command = new ProductCommand.Update(
                "아이폰 15 Pro", savedCategory.getId(), 1500000L,
                100000L, null, ProductStatus.SALE
            );

            // Act & Assert
            assertThatThrownBy(() -> productFacade.updateProduct("loopers.admin", product.getId(), command))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("관리자가 아니면 FORBIDDEN 예외가 발생한다")
        void throwsForbidden_whenNotAdmin() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );
            ProductCommand.Update command = new ProductCommand.Update(
                "아이폰 15 Pro", savedCategory.getId(), 1800000L,
                null, null, ProductStatus.SALE
            );

            // Act & Assert
            assertThatThrownBy(() -> productFacade.updateProduct("invalid.ldap", product.getId(), command))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }

        @Test
        @DisplayName("상품 수정 후 Redis 캐시가 삭제된다")
        void evictsCache_whenProductUpdated() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );
            productFacade.getProduct(product.getId()); // 캐시 저장
            assertThat(productDetailCacheRepository.get(product.getId())).isPresent();

            ProductCommand.Update command = new ProductCommand.Update(
                "아이폰 15 Pro", savedCategory.getId(), 1800000L,
                null, null, ProductStatus.SALE
            );

            // Act
            productFacade.updateProduct("loopers.admin", product.getId(), command);

            // Assert
            assertThat(productDetailCacheRepository.get(product.getId())).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProductDetail (Admin)")
    class GetProductDetail {

        @Test
        @DisplayName("관리자가 상품 상세 정보를 조회한다")
        void returnsProductDetail() {
            // Arrange
            List<ProductOption> options = List.of(
                new ProductOption(null, "256GB", "256GB", 0L, 100)
            );
            List<ProductImage> images = List.of(
                new ProductImage(null, ImageType.MAIN, "https://example.com/main.jpg", "메인 이미지")
            );
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L, options, images)
            );

            // Act
            ProductAdminDetailInfo result = productFacade.getProductDetail("loopers.admin", product.getId());

            // Assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(product.getId()),
                () -> assertThat(result.name()).isEqualTo("아이폰 15"),
                () -> assertThat(result.options()).hasSize(1),
                () -> assertThat(result.images()).hasSize(1)
            );
        }

        @Test
        @DisplayName("관리자가 삭제된 상품도 조회할 수 있다")
        void returnsDeletedProduct() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );
            productFacade.deleteProduct("loopers.admin", product.getId());

            // Act
            ProductAdminDetailInfo result = productFacade.getProductDetail("loopers.admin", product.getId());

            // Assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(product.getId()),
                () -> assertThat(result.deletedAt()).isNotNull()
            );
        }

        @Test
        @DisplayName("관리자가 아니면 FORBIDDEN 예외가 발생한다")
        void throwsForbidden_whenNotAdmin() {
            // Arrange
            Product product = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );

            // Act & Assert
            assertThatThrownBy(() -> productFacade.getProductDetail("invalid.ldap", product.getId()))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }
    }

    @Nested
    @DisplayName("getProductsForAdmin")
    class GetProductsForAdmin {

        @Test
        @DisplayName("관리자가 페이지로 상품 목록을 조회한다")
        void returnsPagedProducts() {
            // Arrange
            productRepository.save(new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L));
            productRepository.save(new Product("갤럭시 S24", savedBrand.getId(), savedCategory.getId(), 1400000L));
            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<ProductAdminDetailInfo> result = productFacade.getProductsForAdmin("loopers.admin", pageable);

            // Assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(2),
                () -> assertThat(result.getTotalElements()).isEqualTo(2)
            );
        }

        @Test
        @DisplayName("관리자가 삭제된 상품도 포함하여 조회할 수 있다")
        void includesDeletedProducts() {
            // Arrange
            Product activeProduct = productRepository.save(
                new Product("아이폰 15", savedBrand.getId(), savedCategory.getId(), 1500000L)
            );
            Product deletedProduct = productRepository.save(
                new Product("갤럭시 S24", savedBrand.getId(), savedCategory.getId(), 1400000L)
            );
            productFacade.deleteProduct("loopers.admin", deletedProduct.getId());
            Pageable pageable = PageRequest.of(0, 10);

            // Act
            Page<ProductAdminDetailInfo> result = productFacade.getProductsForAdmin("loopers.admin", pageable);

            // Assert
            assertAll(
                () -> assertThat(result.getContent()).hasSize(2),
                () -> assertThat(result.getContent())
                    .anyMatch(p -> p.deletedAt() != null)
            );
        }

        @Test
        @DisplayName("관리자가 아니면 FORBIDDEN 예외가 발생한다")
        void throwsForbidden_whenNotAdmin() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);

            // Act & Assert
            assertThatThrownBy(() -> productFacade.getProductsForAdmin("invalid.ldap", pageable))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> assertThat(((CoreException) ex).getErrorType()).isEqualTo(ErrorType.FORBIDDEN));
        }
    }
}