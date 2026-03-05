package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.ConcurrentTestHelper;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

class ProductServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
    }

    @DisplayName("상품을 등록할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 정보를 입력하면, 상품이 DB에 저장된다.")
        @Test
        void savesProductToDatabase_whenValidInputProvided() {
            // act
            Product result = productService.create(
                    new ProductSpec(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명")
            );

            // assert
            var savedProduct = productRepository.findById(result.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(savedProduct.getId()).isEqualTo(result.getId()),
                    () -> assertThat(savedProduct.getBrandId()).isEqualTo(brandId),
                    () -> assertThat(savedProduct.getName().getValue()).isEqualTo("상품명"),
                    () -> assertThat(savedProduct.getThumbnailUrl().getValue()).isEqualTo("https://example.com/thumb.png"),
                    () -> assertThat(savedProduct.getPrice().getAmount()).isEqualTo(10000L),
                    () -> assertThat(savedProduct.getStock().getValue()).isEqualTo(100L),
                    () -> assertThat(savedProduct.getDescription()).isEqualTo("상품 설명")
            );
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    class GetProducts {

        @DisplayName("전체 상품이 반환된다.")
        @Test
        void returnsAllProducts() {
            // arrange
            createProduct(brandId, "상품 1", 10000L, 100L);
            createProduct(brandId, "상품 2", 20000L, 200L);

            // act
            Page<Product> products = productService.getProducts(null, new PageSize(0, 10));

            // assert
            assertThat(products.content()).hasSize(2);
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsProductsSortedByCreatedAtDesc() {
            // arrange
            var productId1 = createProduct(brandId, "상품 1", 10000L, 100L);
            var productId2 = createProduct(brandId, "상품 2", 20000L, 200L);

            // act
            Page<Product> products = productService.getProducts(null, new PageSize(0, 10));

            // assert
            assertThat(products.content())
                    .extracting(Product::getId)
                    .containsExactly(productId2, productId1);
        }

        @DisplayName("상품이 없으면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoProducts() {
            // act
            Page<Product> products = productService.getProducts(null, new PageSize(0, 10));

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
            Page<Product> products = productService.getProducts(null, new PageSize(0, 2));

            // assert
            assertAll(
                    () -> assertThat(products.content()).hasSize(2),
                    () -> assertThat(products.hasNext()).isTrue()
            );
        }
    }

    @DisplayName("브랜드별 상품 목록을 조회할 때,")
    @Nested
    class GetProductsByBrandId {

        @DisplayName("해당 브랜드의 상품만 반환된다.")
        @Test
        void returnsOnlyProductsOfGivenBrand() {
            // arrange
            var otherBrandId = brandService.create(new com.loopers.domain.brand.NewBrand("브랜드 2", "logo2.png", "설명 2")).getId();
            createProduct(brandId, "상품 1", 10000L, 100L);
            productService.create(new ProductSpec(otherBrandId, "상품 2", "thumb2.png", 20000L, 200L, "설명"));

            // act
            Page<Product> products = productService.getProducts(brandId, new PageSize(0, 10));

            // assert
            assertAll(
                    () -> assertThat(products.content()).hasSize(1),
                    () -> assertThat(products.content().get(0).getName().getValue()).isEqualTo("상품 1")
            );
        }
    }

    @DisplayName("상품 정보를 수정할 때,")
    @Nested
    class Update {

        @DisplayName("유효한 정보를 입력하면, 상품 정보가 수정된다.")
        @Test
        void updatesProduct_whenValidInputProvided() {
            // arrange
            var productId = createProduct(brandId);

            // act
            productService.update(new ModifyProduct(productId,"수정된 상품명", "https://example.com/new-thumb.png", 20000L, 200L, "수정된 설명"));

            // assert
            var updatedProduct = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(updatedProduct.getName().getValue()).isEqualTo("수정된 상품명"),
                    () -> assertThat(updatedProduct.getThumbnailUrl().getValue()).isEqualTo("https://example.com/new-thumb.png"),
                    () -> assertThat(updatedProduct.getPrice().getAmount()).isEqualTo(20000L),
                    () -> assertThat(updatedProduct.getStock().getValue()).isEqualTo(200L),
                    () -> assertThat(updatedProduct.getDescription()).isEqualTo("수정된 설명")
            );
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // act & assert
            assertThatThrownBy(() -> productService.update(new ModifyProduct(999L,"상품명", "thumb.png", 10000L, 100L, "설명")))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("상품을 삭제할 때,")
    @Nested
    class Delete {

        @DisplayName("유효한 상품이면, 상품이 소프트 삭제된다.")
        @Test
        void softDeletesProduct_whenProductExists() {
            // arrange
            var productId = createProduct(brandId);

            // act
            productService.delete(productId);

            // assert
            var deleted = productRepository.findById(productId).orElseThrow();
            assertThat(deleted.getDeletedAt()).isNotNull();
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // act & assert
            assertThatThrownBy(() -> productService.delete(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }

    @DisplayName("재고를 차감할 때,")
    @Nested
    class DeductStock {

        @DisplayName("유효한 상품이면, 재고가 차감된다.")
        @Test
        void deductsStock_whenActiveProductExists() {
            // arrange
            var productId = createProduct(brandId, "상품", 10000L, 100L);

            // act
            productService.deductStock(productId, 3L);

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getStock().getValue()).isEqualTo(97L);
        }

        @DisplayName("품절 상품이면, SOLD_OUT_PRODUCT 예외가 발생한다.")
        @Test
        void throwsException_whenSoldOut() {
            // arrange
            var productId = createProduct(brandId, "상품", 10000L, 1L);
            productService.deductStock(productId, 1L);

            // act & assert
            assertThatThrownBy(() -> productService.deductStock(productId, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.SOLD_OUT_PRODUCT));
        }

        @DisplayName("재고가 부족하면, INSUFFICIENT_STOCK 예외가 발생한다.")
        @Test
        void throwsException_whenInsufficientStock() {
            // arrange
            var productId = createProduct(brandId, "상품", 10000L, 5L);

            // act & assert
            assertThatThrownBy(() -> productService.deductStock(productId, 10L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_STOCK));
        }

        @DisplayName("삭제된 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createProduct(brandId);
            productService.delete(productId);

            // act & assert
            assertThatThrownBy(() -> productService.deductStock(productId, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("동시에 재고를 차감하면, 비관적 락에 의해 정확한 재고가 유지된다.")
        @Test
        void maintainsCorrectStock_whenConcurrentDeductions() throws InterruptedException {
            // arrange
            var productId = createProduct(brandId, "상품", 10000L, 100L);
            int threadCount = 10;

            // act
            var result = ConcurrentTestHelper.executeConcurrently(
                    threadCount,
                    () -> productService.deductStock(productId, 1L)
            );

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(result.successCount()).isEqualTo(threadCount),
                    () -> assertThat(result.failCount()).isZero(),
                    () -> assertThat(product.getStock().getValue()).isEqualTo(90L)
            );
        }

        @DisplayName("비관적 락이 점유된 상태에서 다른 트랜잭션이 락을 요청하면, lock timeout 내에 예외가 발생한다.")
        @Test
        void throwsExceptionWithinLockTimeout_whenLockIsAlreadyHeld() throws InterruptedException {
            // arrange
            var productId = createProduct(brandId, "상품", 10000L, 100L);
            long lockHoldTimeMs = 5000L;
            CountDownLatch lockAcquired = new CountDownLatch(1);
            CountDownLatch testDone = new CountDownLatch(1);
            AtomicReference<Exception> threadBException = new AtomicReference<>();
            AtomicLong threadBWaitTimeMs = new AtomicLong(0);

            // act - Thread A: 비관적 락을 걸고 5초간 점유
            ExecutorService executorService = Executors.newFixedThreadPool(2);
            executorService.execute(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    productRepository.findByIdAndDeletedAtIsNullForUpdate(productId);
                    lockAcquired.countDown();
                    try {
                        Thread.sleep(lockHoldTimeMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                testDone.countDown();
            });

            // Thread B: 락 획득 대기 후 같은 상품에 락 시도
            lockAcquired.await();
            executorService.execute(() -> {
                long start = System.currentTimeMillis();
                try {
                    transactionTemplate.executeWithoutResult(status ->
                            productRepository.findByIdAndDeletedAtIsNullForUpdate(productId)
                    );
                } catch (Exception e) {
                    threadBException.set(e);
                }
                threadBWaitTimeMs.set(System.currentTimeMillis() - start);
                testDone.countDown();
            });

            testDone.await();
            executorService.shutdown();

            // assert - Thread B가 lock timeout(2초) 이내에 예외 발생해야 함
            // MySQL이 JPA lock.timeout 힌트를 무시하면 5초 후에야 성공하므로 대기 시간으로 판별
            long waitTime = threadBWaitTimeMs.get();
            assertAll(
                    () -> assertThat(threadBException.get()).isNotNull(),
                    () -> assertThat(waitTime).isLessThan(lockHoldTimeMs)
            );
        }
    }

    @DisplayName("좋아요 수를 증가시킬 때,")
    @Nested
    class IncreaseLikeCount {

        @DisplayName("활성 상품이면, 좋아요 수가 1 증가한다.")
        @Test
        void incrementsLikeCount_whenActiveProductExists() {
            // arrange
            var productId = createProduct(brandId);

            // act
            productService.increaseLikeCount(productId);

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("동시에 10명이 좋아요하면, 아토믹 업데이트에 의해 likeCount가 정확히 10이 된다.")
        @Test
        void maintainsCorrectLikeCount_whenConcurrentIncrements() throws InterruptedException {
            // arrange
            var productId = createProduct(brandId);
            int threadCount = 10;

            // act
            var result = ConcurrentTestHelper.executeConcurrently(
                    threadCount,
                    () -> productService.increaseLikeCount(productId)
            );

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(result.successCount()).isEqualTo(threadCount),
                    () -> assertThat(result.failCount()).isZero(),
                    () -> assertThat(product.getLikeCount()).isEqualTo(10L)
            );
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때,")
    @Nested
    class DecreaseLikeCount {

        @DisplayName("활성 상품이면, 좋아요 수가 1 감소한다.")
        @Test
        void decrementsLikeCount_whenActiveProductExists() {
            // arrange
            var productId = createProduct(brandId);
            productService.increaseLikeCount(productId);

            // act
            productService.decreaseLikeCount(productId);

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getLikeCount()).isZero();
        }

        @DisplayName("동시에 10명이 좋아요를 취소하면, 아토믹 업데이트에 의해 likeCount가 정확히 0이 된다.")
        @Test
        void maintainsCorrectLikeCount_whenConcurrentDecrements() throws InterruptedException {
            // arrange
            var productId = createProduct(brandId);
            int threadCount = 10;
            for (int i = 0; i < threadCount; i++) {
                productService.increaseLikeCount(productId);
            }

            // act
            var result = ConcurrentTestHelper.executeConcurrently(
                    threadCount,
                    () -> productService.decreaseLikeCount(productId)
            );

            // assert
            var product = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(result.successCount()).isEqualTo(threadCount),
                    () -> assertThat(result.failCount()).isZero(),
                    () -> assertThat(product.getLikeCount()).isZero()
            );
        }
    }

    @DisplayName("활성 상품 존재 여부를 검증할 때,")
    @Nested
    class ValidateActiveProductExists {

        @DisplayName("활성 상품이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenActiveProductExists() {
            // arrange
            var productId = createProduct(brandId);

            // act & assert
            productService.validateActiveProductExists(productId);
        }

        @DisplayName("삭제된 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductIsDeleted() {
            // arrange
            var productId = createProduct(brandId);
            productService.delete(productId);

            // act & assert
            assertThatThrownBy(() -> productService.validateActiveProductExists(productId))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenProductNotFound() {
            // act & assert
            assertThatThrownBy(() -> productService.validateActiveProductExists(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND));
        }
    }
}
