package com.loopers.application.product;

import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductStockApplicationServiceConcurrencyTest {

    private final ProductStockApplicationService productStockApplicationService;
    private final ProductLikeAplicationService productLikeAplicationService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductStockApplicationServiceConcurrencyTest(
            ProductStockApplicationService productStockApplicationService,
            ProductLikeAplicationService productLikeAplicationService,
            ProductRepository productRepository,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.productStockApplicationService = productStockApplicationService;
        this.productLikeAplicationService = productLikeAplicationService;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("원자 업데이트 재고 차감: 재고 10, 요청 20 동시 실행 시 10건만 성공한다")
    void atomicStrategy_concurrency() throws InterruptedException {
        UUID productId = createStockProduct(10);
        StockConcurrencyMetrics metrics = runConcurrentDeduction(productId, 20,
                ignored -> productStockApplicationService.decreaseStockWithAtomicUpdate(productId, 1));
        printMetrics("ATOMIC_UPDATE", metrics);

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(metrics.failureCount()).isZero();
        assertThat(metrics.successCount()).isEqualTo(10);
        assertThat(metrics.outOfStockCount()).isEqualTo(10);
        assertThat(updated.stock()).isZero();
    }

    @Test
    @DisplayName("좋아요(원자 업데이트) + 원자 재고 차감을 동시에 요청하면 좋아요는 모두 성공하고 재고는 10건만 차감된다")
    void mixedLikeAndStock_atomicStockStrategy_concurrency() throws InterruptedException {
        UUID productId = createStockProduct(10);
        MixedLikeAndStockMetrics metrics = runConcurrentLikeAndStock(
                productId,
                20,
                20,
                () -> productStockApplicationService.decreaseStockWithAtomicUpdate(productId, 1)
        );
        printMixedMetrics("ATOMIC_UPDATE", metrics);

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(metrics.likeFailureCount()).isZero();
        assertThat(metrics.likeSuccessCount()).isEqualTo(20);
        assertThat(metrics.stockFailureCount()).isZero();
        assertThat(metrics.stockSuccessCount()).isEqualTo(10);
        assertThat(metrics.stockOutOfStockCount()).isEqualTo(10);
        assertThat(updated.likeCount()).isEqualTo(20);
        assertThat(updated.stock()).isZero();
    }

    private UUID createStockProduct(int stock) {
        UUID brandId = brandRepository.save(new Brand(new BrandName("STOCK_CONC_BRAND"), "", "")).id();
        UUID categoryId = categoryRepository.save(new Category("STOCK_CONC_CATEGORY")).id();
        return productRepository.save(new Product("동시성 재고 상품", 10_000, stock, "desc", categoryId, brandId)).id();
    }

    private StockConcurrencyMetrics runConcurrentDeduction(UUID productId, int threadCount, Consumer<Integer> strategy)
            throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        long startedAtNanos = System.nanoTime();
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    strategy.accept(index);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.BAD_REQUEST) {
                        outOfStockCount.incrementAndGet();
                    } else if (e.getErrorType() == ErrorType.CONFLICT) {
                        conflictCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        start.countDown();
        done.await(15, TimeUnit.SECONDS);
        long elapsedNanos = System.nanoTime() - startedAtNanos;
        executorService.shutdownNow();

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        double throughput = elapsedNanos == 0L ? 0D : (successCount.get() * 1_000_000_000D) / elapsedNanos;

        return new StockConcurrencyMetrics(
                productId,
                threadCount,
                successCount.get(),
                outOfStockCount.get(),
                conflictCount.get(),
                failureCount.get(),
                elapsedMillis,
                throughput
        );
    }

    private MixedLikeAndStockMetrics runConcurrentLikeAndStock(
            UUID productId,
            int likeRequestCount,
            int stockRequestCount,
            Runnable stockAction
    ) throws InterruptedException {
        AtomicInteger likeSuccessCount = new AtomicInteger(0);
        AtomicInteger likeFailureCount = new AtomicInteger(0);
        AtomicInteger stockSuccessCount = new AtomicInteger(0);
        AtomicInteger stockOutOfStockCount = new AtomicInteger(0);
        AtomicInteger stockConflictCount = new AtomicInteger(0);
        AtomicInteger stockFailureCount = new AtomicInteger(0);

        int totalThreadCount = likeRequestCount + stockRequestCount;
        ExecutorService executorService = Executors.newFixedThreadPool(totalThreadCount);
        CountDownLatch ready = new CountDownLatch(totalThreadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreadCount);

        long startedAtNanos = System.nanoTime();

        for (int i = 0; i < likeRequestCount; i++) {
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    productLikeAplicationService.increaseLikeCount(productId);
                    likeSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    likeFailureCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        for (int i = 0; i < stockRequestCount; i++) {
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    stockAction.run();
                    stockSuccessCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.BAD_REQUEST) {
                        stockOutOfStockCount.incrementAndGet();
                    } else if (e.getErrorType() == ErrorType.CONFLICT) {
                        stockConflictCount.incrementAndGet();
                    } else {
                        stockFailureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    stockFailureCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        start.countDown();
        done.await(15, TimeUnit.SECONDS);

        long elapsedNanos = System.nanoTime() - startedAtNanos;
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        double likeThroughput = elapsedNanos == 0L ? 0D : (likeSuccessCount.get() * 1_000_000_000D) / elapsedNanos;
        double stockThroughput = elapsedNanos == 0L ? 0D : (stockSuccessCount.get() * 1_000_000_000D) / elapsedNanos;
        executorService.shutdownNow();

        return new MixedLikeAndStockMetrics(
                productId,
                likeRequestCount,
                stockRequestCount,
                likeSuccessCount.get(),
                likeFailureCount.get(),
                stockSuccessCount.get(),
                stockOutOfStockCount.get(),
                stockConflictCount.get(),
                stockFailureCount.get(),
                elapsedMillis,
                likeThroughput,
                stockThroughput
        );
    }

    private void printMetrics(String strategy, StockConcurrencyMetrics metrics) {
        System.out.printf(
                "[STOCK_CONCURRENCY_METRICS] strategy=%s requests=%d success=%d outOfStock=%d conflict=%d failure=%d elapsedMs=%d throughput=%.2f finalStockEstimate=%d%n",
                strategy,
                metrics.requestCount(),
                metrics.successCount(),
                metrics.outOfStockCount(),
                metrics.conflictCount(),
                metrics.failureCount(),
                metrics.elapsedMillis(),
                metrics.successThroughputPerSec(),
                10 - metrics.successCount()
        );
    }

    private void printMixedMetrics(String strategy, MixedLikeAndStockMetrics metrics) {
        System.out.printf(
                "[LIKE_STOCK_MIXED_METRICS] strategy=%s likeRequests=%d stockRequests=%d likeSuccess=%d likeFailure=%d stockSuccess=%d stockOutOfStock=%d stockConflict=%d stockFailure=%d elapsedMs=%d likeThroughput=%.2f stockThroughput=%.2f finalStockEstimate=%d%n",
                strategy,
                metrics.likeRequestCount(),
                metrics.stockRequestCount(),
                metrics.likeSuccessCount(),
                metrics.likeFailureCount(),
                metrics.stockSuccessCount(),
                metrics.stockOutOfStockCount(),
                metrics.stockConflictCount(),
                metrics.stockFailureCount(),
                metrics.elapsedMillis(),
                metrics.likeSuccessThroughputPerSec(),
                metrics.stockSuccessThroughputPerSec(),
                10 - metrics.stockSuccessCount()
        );
    }

    record StockConcurrencyMetrics(
            UUID productId,
            int requestCount,
            int successCount,
            int outOfStockCount,
            int conflictCount,
            int failureCount,
            long elapsedMillis,
            double successThroughputPerSec
    ) {
    }

    record MixedLikeAndStockMetrics(
            UUID productId,
            int likeRequestCount,
            int stockRequestCount,
            int likeSuccessCount,
            int likeFailureCount,
            int stockSuccessCount,
            int stockOutOfStockCount,
            int stockConflictCount,
            int stockFailureCount,
            long elapsedMillis,
            double likeSuccessThroughputPerSec,
            double stockSuccessThroughputPerSec
    ) {
    }
}
