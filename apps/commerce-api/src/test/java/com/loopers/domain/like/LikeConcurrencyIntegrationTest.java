package com.loopers.domain.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 좋아요 동시성 테스트.
 * 동일 상품에 여러 사용자가 좋아요/취소해도 좋아요 수가 정상 반영되는지 검증. (05-transaction-query §12.3)
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class LikeConcurrencyIntegrationTest {

    @Autowired
    private LikeService likeService;
    @Autowired
    private ProductService productService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 여러 사용자가 동시에 좋아요해도 좋아요 수가 정상 반영된다.")
    @Test
    void concurrency_multipleUsersLikeSameProduct_likeCountIsCorrect() throws InterruptedException {
        Long brandId = brandService.registerBrand("브랜드").getId();
        Long productId = productService.registerProduct(brandId, "상품", new BigDecimal("1000"), 10).getId();
        int userCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (long userId = 1L; userId <= userCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    start.await();
                    likeService.addLike(uid, productId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("이미 좋아요")) {
                        conflictCount.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        long likeCount = likeService.getLikeCount(productId);
        assertThat(likeCount).isEqualTo(successCount.get());
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(userCount);
        assertThat(likeCount).isBetween(1L, (long) userCount);
    }

    @DisplayName("동일 상품에 좋아요 후 여러 사용자가 동시에 취소해도 좋아요 수가 정상 반영된다.")
    @Test
    void concurrency_multipleUsersRemoveLike_likeCountIsCorrect() throws InterruptedException {
        Long brandId = brandService.registerBrand("브랜드").getId();
        Long productId = productService.registerProduct(brandId, "상품", new BigDecimal("1000"), 10).getId();
        int userCount = 5;
        for (long userId = 1L; userId <= userCount; userId++) {
            likeService.addLike(userId, productId);
        }
        assertThat(likeService.getLikeCount(productId)).isEqualTo(userCount);

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(userCount);
        AtomicInteger removeCount = new AtomicInteger(0);

        for (long userId = 1L; userId <= userCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    start.await();
                    likeService.removeLike(uid, productId);
                    removeCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        assertThat(likeService.getLikeCount(productId)).isEqualTo(0L);
        assertThat(removeCount.get()).isEqualTo(userCount);
    }
}
