package com.loopers.application.like;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private Long createProduct() {
        var brandResult = brandService.createBrand("브랜드명", "https://example.com/logo.png", "브랜드 설명");
        var command = new ProductCommand.CreateProductCommand(
                brandResult.id(), "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"
        );
        return productService.createProduct(command);
    }
}
