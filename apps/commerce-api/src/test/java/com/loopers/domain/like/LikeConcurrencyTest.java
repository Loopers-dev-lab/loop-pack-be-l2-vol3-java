package com.loopers.domain.like;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class LikeConcurrencyTest {

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("좋아요 동시성 테스트: ")
    @Nested
    class LikeConcurrency {

        @DisplayName("동일 유저가 같은 상품에 동시에 좋아요를 요청해도, 좋아요는 1건만 저장된다.")
        @Test
        void concurrentLike_sameUser_onlyOneSaved() throws InterruptedException {
            // arrange
            int threadCount = 10;
            Long userId = 1L;
            Long productId = 100L;

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        likeService.like(userId, productId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // assert
            long count = likeRepository.countByProductId(productId);

            assertAll(
                () -> assertThat(successCount.get()).as("모든 요청이 성공").isEqualTo(threadCount),
                () -> assertThat(count).as("좋아요는 1건만 저장").isEqualTo(1L)
            );
        }

        @DisplayName("여러 유저가 같은 상품에 동시에 좋아요를 요청하면, 각각 1건씩 정상 저장된다.")
        @Test
        void concurrentLike_differentUsers_allSaved() throws InterruptedException {
            // arrange
            int threadCount = 10;
            Long productId = 100L;

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                final long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();
                        likeService.like(userId, productId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // assert
            long count = likeRepository.countByProductId(productId);

            assertAll(
                () -> assertThat(successCount.get()).as("모든 요청이 성공").isEqualTo(threadCount),
                () -> assertThat(count).as("좋아요 수 = 유저 수").isEqualTo(threadCount)
            );
        }
    }
}
