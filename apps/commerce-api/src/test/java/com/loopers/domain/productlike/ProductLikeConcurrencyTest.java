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
import java.util.concurrent.TimeUnit;
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
    private ProductLikeRepository productLikeRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("10명이 동시에 좋아요를 등록하면 모든 좋아요가 정확히 기록된다")
    void registerLike_Concurrency() throws InterruptedException {
        // Given
        Brand brand = brandRepository.save(Brand.create("테스트브랜드", null, null));
        Product product = productRepository.save(
                Product.create(brand.getId(), "동시성테스트상품", null, new BigDecimal("10000"), 100, null)
        );
        Long productId = product.getId();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
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

        // Then - 좋아요 등록은 모두 성공해야 한다
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isEqualTo(0);

        // product_likes 레코드가 정확히 생성되어야 한다
        int likeCount = productLikeRepository.findAllByProductId(productId).size();
        assertThat(likeCount).isEqualTo(threadCount);

        // likesCount는 @Async + AFTER_COMMIT 이벤트로 비동기 업데이트되므로 대기
        TimeUnit.SECONDS.sleep(2);
        Product updatedProduct = productRepository.findById(productId).orElseThrow();
        assertThat(updatedProduct.getLikesCount()).isEqualTo(threadCount);
    }
}
