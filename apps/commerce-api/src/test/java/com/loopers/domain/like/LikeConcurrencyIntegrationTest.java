package com.loopers.domain.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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
import java.util.concurrent.atomic.AtomicReference;

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
        AtomicReference<Throwable> firstUnexpected = new AtomicReference<>();

        for (long userId = 1L; userId <= userCount; userId++) {
            final long uid = userId;
            executor.submit(() -> {
                try {
                    start.await();
                    likeService.addLike(uid, productId);
                    successCount.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.CONFLICT && e.getMessage() != null && e.getMessage().contains("이미 좋아요")) {
                        conflictCount.incrementAndGet();
                    } else {
                        firstUnexpected.compareAndSet(null, e);
                    }
                } catch (Throwable t) {
                    firstUnexpected.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        if (firstUnexpected.get() != null) {
            throw new AssertionError("예상치 못한 예외(데드락/타임아웃 등은 재시도로 처리되어야 함)", firstUnexpected.get());
        }
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(userCount)
                .as("모든 요청은 성공 또는 CONFLICT로 처리되어야 함");

        long likeCount = likeService.getLikeCount(productId);
        long likeCountFromStats = likeService.getLikeCountFromStats(productId);
        assertThat(likeCount).isEqualTo(likeCountFromStats)
                .as("likes 테이블과 product_stats like_count는 동기화되어 있어야 함");
        assertThat(likeCount).isEqualTo(successCount.get())
                .as("성공한 건수만큼 DB에 원자적으로 반영되어야 함");
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
