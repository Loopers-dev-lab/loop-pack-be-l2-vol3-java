package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductLikeAplicationServiceConcurrencyTest {

    private final ProductLikeAplicationService productLikeAplicationService;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    ProductLikeAplicationServiceConcurrencyTest(
            ProductLikeAplicationService productLikeAplicationService,
            ProductRepository productRepository,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
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
    @DisplayName("좋아요 카운트 증가를 동시에 요청하면 요청 수만큼 정확히 증가한다")
    void increaseLikeCount_concurrentRequests_increasesExactly() throws InterruptedException {
        Long brandId = brandRepository.save(new Brand(new BrandName("LIKE_CONC_BRAND"), "", "")).id();
        Long categoryId = categoryRepository.save(new Category("LIKE_CONC_CATEGORY")).id();
        Long productId = productRepository.save(new Product("동시성 증가 상품", 10_000, 10, "desc", categoryId, brandId)).id();

        int threadCount = 50;
        AtomicInteger failures = runConcurrently(threadCount, () -> productLikeAplicationService.increaseLikeCount(productId));

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(failures.get()).isZero();
        assertThat(updated.likeCount()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("좋아요 카운트 감소를 동시에 요청해도 0 미만으로 내려가지 않는다")
    void decreaseLikeCount_concurrentRequests_neverGoesBelowZero() throws InterruptedException {
        Long brandId = brandRepository.save(new Brand(new BrandName("LIKE_CONC_BRAND_2"), "", "")).id();
        Long categoryId = categoryRepository.save(new Category("LIKE_CONC_CATEGORY_2")).id();
        Long productId = productRepository.save(new Product(null, "동시성 감소 상품", 10_000, 10, "desc", categoryId, brandId, 5, null)).id();

        AtomicInteger failures = runConcurrently(30, () -> productLikeAplicationService.decreaseLikeCount(productId));

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(failures.get()).isZero();
        assertThat(updated.likeCount()).isZero();
    }

    private AtomicInteger runConcurrently(int threadCount, Runnable action) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(3, TimeUnit.SECONDS);
        start.countDown();
        done.await(10, TimeUnit.SECONDS);
        executorService.shutdownNow();
        return failures;
    }
}
