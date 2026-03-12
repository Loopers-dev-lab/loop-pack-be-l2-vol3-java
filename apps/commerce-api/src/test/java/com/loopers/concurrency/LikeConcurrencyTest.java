package com.loopers.concurrency;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.domain.common.Money;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("좋아요 동시성 테스트")
class LikeConcurrencyTest {

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("여러 사용자가 동시에 같은 상품에 좋아요를 해도 좋아요 수가 정상 반영된다")
    void 동시_좋아요_정상_반영() throws InterruptedException {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );

        int userCount = 50;  // 50명이 동시에 좋아요
        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act
        for (int i = 0; i < userCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    likeApplicationService.like(userId, product.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Assert: 모든 좋아요가 성공해야 함
        assertThat(successCount.get()).isEqualTo(userCount);
        assertThat(failCount.get()).isEqualTo(0);

        // 좋아요 수 확인
        long likeCount = likeRepository.countByProductId(product.getId());
        assertThat(likeCount).isEqualTo(userCount);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 좋아요를 해도 1번만 성공한다")
    void 동시_중복_좋아요_방지() throws InterruptedException {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );

        Long userId = 1L;
        int threadCount = 10;  // 같은 사용자가 10번 동시 좋아요 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    likeApplicationService.like(userId, product.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Assert: 1번만 성공해야 함
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // 좋아요 수 확인
        long likeCount = likeRepository.countByProductId(product.getId());
        assertThat(likeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 사용자가 좋아요와 취소를 동시에 해도 최종 좋아요 수가 정확하다")
    void 동시_좋아요_취소_정합성() throws InterruptedException {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );

        // 먼저 10명이 좋아요 등록
        int initialLikeCount = 10;
        for (int i = 1; i <= initialLikeCount; i++) {
            likeApplicationService.like((long) i, product.getId());
        }

        // 동시에: 기존 10명 취소 + 새로운 10명 좋아요
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger likeSuccessCount = new AtomicInteger();
        AtomicInteger unlikeSuccessCount = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index < 10) {
                        // 기존 사용자 1~10 취소
                        likeApplicationService.unlike((long) (index + 1), product.getId());
                        unlikeSuccessCount.incrementAndGet();
                    } else {
                        // 새 사용자 11~20 좋아요
                        likeApplicationService.like((long) (index + 1), product.getId());
                        likeSuccessCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // Assert: 기존 10명 취소, 새로운 10명 좋아요 → 최종 10개
        assertThat(unlikeSuccessCount.get()).isEqualTo(10);
        assertThat(likeSuccessCount.get()).isEqualTo(10);

        long finalLikeCount = likeRepository.countByProductId(product.getId());
        assertThat(finalLikeCount).isEqualTo(10);
    }
}
