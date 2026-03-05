package com.loopers.domain.productlike;

import com.loopers.application.productlike.ProductLikeFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("상품 좋아요 동시성 테스트")
class ProductLikeConcurrencyTest {

    @Autowired
    private ProductLikeFacade productLikeFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("50명이 동시에 좋아요를 등록하면 likesCount가 정확히 50이 된다")
    void increaseLikes_Concurrency() throws InterruptedException {
        // Given
        Brand brand = brandRepository.save(Brand.create("테스트브랜드", null, null));
        Product product = productRepository.save(
                Product.create(brand.getId(), "동시성테스트상품", null, new BigDecimal("10000"), 100, null)
        );
        Long productId = product.getId();

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executorService.submit(() -> {
                try {
                    productLikeFacade.registerLike(userId, productId);
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

        // Then
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        assertThat(updatedProduct.getLikesCount()).isEqualTo(50);
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(0);
    }
}
