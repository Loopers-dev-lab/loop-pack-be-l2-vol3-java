package com.loopers.application.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.like.persistence.LikeJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest
class LikeServiceIntegrationTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요를 등록할 때,")
    @Nested
    class LikeProduct {

        @DisplayName("유효한 요청이면, 좋아요가 저장된다.")
        @Test
        void savesLikeToDatabase_whenValidInputProvided() {
            // arrange
            var productId = createProduct();
            var userId = 1L;

            // act
            likeService.likeProduct(userId, productId);

            // assert
            assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isTrue();
        }

        @DisplayName("이미 좋아요가 존재하면, 아무 동작 없이 성공한다. (멱등성)")
        @Test
        void doesNothing_whenLikeAlreadyExists() {
            // arrange
            var productId = createProduct();
            var userId = 1L;
            likeService.likeProduct(userId, productId);

            // act & assert
            assertThatCode(() -> likeService.likeProduct(userId, productId))
                    .doesNotThrowAnyException();
            assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isTrue();
        }

        @DisplayName("동일한 사용자가 동시에 좋아요를 요청하면, 하나만 성공하고 좋아요는 1개만 생성된다.")
        @Test
        void onlyOneLikeCreated_whenConcurrentLikeRequests() throws InterruptedException {
            // arrange
            var productId = createProduct();
            var userId = 1L;
            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.execute(() -> {
                    try {
                        likeService.likeProduct(userId, productId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executorService.shutdown();

            // assert
            assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
            assertThat(likeJpaRepository.count()).isEqualTo(1);
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // act & assert
            assertThatThrownBy(() -> likeService.likeProduct(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("삭제된 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createProduct();
            var product = productRepository.findById(productId).orElseThrow();
            product.delete();
            productRepository.save(product);

            // act & assert
            assertThatThrownBy(() -> likeService.likeProduct(1L, productId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("좋아요를 취소할 때,")
    @Nested
    class UnlikeProduct {

        @DisplayName("유효한 요청이면, 좋아요가 삭제된다.")
        @Test
        void deletesLikeFromDatabase_whenValidInputProvided() {
            // arrange
            var productId = createProduct();
            var userId = 1L;
            likeService.likeProduct(userId, productId);

            // act
            likeService.unlikeProduct(userId, productId);

            // assert
            assertThat(likeJpaRepository.existsByUserIdAndProductId(userId, productId)).isFalse();
        }

        @DisplayName("좋아요가 존재하지 않으면, 아무 동작 없이 성공한다. (멱등성)")
        @Test
        void doesNothing_whenLikeDoesNotExist() {
            // arrange
            var productId = createProduct();
            var userId = 1L;

            // act & assert
            assertThatCode(() -> likeService.unlikeProduct(userId, productId))
                    .doesNotThrowAnyException();
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // act & assert
            assertThatThrownBy(() -> likeService.unlikeProduct(1L, 999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("삭제된 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createProduct();
            var product = productRepository.findById(productId).orElseThrow();
            product.delete();
            productRepository.save(product);

            // act & assert
            assertThatThrownBy(() -> likeService.unlikeProduct(1L, productId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("좋아요 등록한 상품 목록을 조회할 때,")
    @Nested
    class GetLikedProducts {

        @DisplayName("좋아요한 상품이 있으면, 상품 정보와 좋아요 수가 정확히 반환된다.")
        @Test
        void returnsLikedProductsWithCorrectLikeCount_whenUserHasLikes() {
            // arrange
            var productId = createProduct();
            likeService.likeProduct(1L, productId);
            likeService.likeProduct(2L, productId);
            likeService.likeProduct(3L, productId);

            // act
            Page<LikedProductResult> result = likeService.getLikedProducts(1L, new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(1),
                    () -> assertThat(result.content().get(0).productId()).isEqualTo(productId),
                    () -> assertThat(result.content().get(0).productName()).isEqualTo("상품명"),
                    () -> assertThat(result.content().get(0).likeCount()).isEqualTo(3L),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenUserHasNoLikes() {
            // arrange
            var userId = 1L;

            // act
            Page<LikedProductResult> result = likeService.getLikedProducts(userId, new PageSize(0, 20));

            // assert
            assertAll(
                    () -> assertThat(result.content()).isEmpty(),
                    () -> assertThat(result.hasNext()).isFalse()
            );
        }

        @DisplayName("삭제된 상품은 조회 대상에서 제외된다.")
        @Test
        void excludesDeletedProducts_whenProductIsDeleted() {
            // arrange
            var productId = createProduct();
            likeService.likeProduct(1L, productId);
            var product = productRepository.findById(productId).orElseThrow();
            product.delete();
            productRepository.save(product);

            // act
            Page<LikedProductResult> result = likeService.getLikedProducts(1L, new PageSize(0, 20));

            // assert
            assertThat(result.content()).isEmpty();
        }

        @DisplayName("좋아요한 상품이 페이지 크기보다 많으면, hasNext가 true이다.")
        @Test
        void supportsPagination_whenMultipleProductsLiked() {
            // arrange
            var userId = 1L;
            var brandResult = brandService.createBrand("페이지브랜드", "https://example.com/logo.png", "브랜드 설명");
            var productId1 = productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품1", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"));
            var productId2 = productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품2", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"));
            var productId3 = productService.createProduct(new ProductCommand.CreateProductCommand(
                    brandResult.id(), "상품3", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"));
            likeService.likeProduct(userId, productId1);
            likeService.likeProduct(userId, productId2);
            likeService.likeProduct(userId, productId3);

            // act
            Page<LikedProductResult> result = likeService.getLikedProducts(userId, new PageSize(0, 2));

            // assert
            assertAll(
                    () -> assertThat(result.content()).hasSize(2),
                    () -> assertThat(result.hasNext()).isTrue()
            );
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
