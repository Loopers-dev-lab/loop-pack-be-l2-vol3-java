package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("좋아요 동시성 테스트")
class LikeConcurrencyIntegrationTest {

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @BeforeEach
    void setUp() {
        Brand brand = brandService.register("나이키");
        brandId = brand.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 상품에 동시 좋아요 시, ")
    @Nested
    class ConcurrentLike {

        @DisplayName("모든 좋아요가 성공하고 likeCount가 정확히 반영된다.")
        @Test
        void allLikesSucceed_whenConcurrent() throws InterruptedException {
            int threadCount = 5;
            Product product = productService.register(brandId, "에어맥스", 129000);

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
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
            executorService.shutdown();

            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failCount.get()).isEqualTo(0);

            // likeCount는 @Async AFTER_COMMIT 이벤트로 eventual consistency 반영
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Product result = productService.getById(product.getId());
                assertThat(result.getLikeCount()).isEqualTo(threadCount);
            });
        }
    }

    @DisplayName("동일 상품에 동시 좋아요/싫어요 시, ")
    @Nested
    class ConcurrentLikeAndUnlike {

        @DisplayName("likeCount가 정확히 반영된다.")
        @Test
        void likeCountIsCorrect_whenConcurrentLikeAndUnlike() throws InterruptedException {
            int likeCount = 3;
            int unlikeCount = 3;
            int totalThreads = likeCount + unlikeCount;
            Product product = productService.register(brandId, "에어맥스", 129000);

            // 먼저 unlikeCount명이 좋아요를 등록 (순차)
            for (int i = 0; i < unlikeCount; i++) {
                likeApplicationService.like((long) (i + 1), product.getId());
            }

            // 순차 좋아요의 likeCount 반영 대기
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Product p = productService.getById(product.getId());
                assertThat(p.getLikeCount()).isEqualTo(unlikeCount);
            });

            ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch latch = new CountDownLatch(totalThreads);

            // 새로운 유저 likeCount명이 좋아요
            for (int i = 0; i < likeCount; i++) {
                long userId = unlikeCount + i + 1;
                executorService.submit(() -> {
                    try {
                        likeApplicationService.like(userId, product.getId());
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 기존 유저 unlikeCount명이 싫어요
            for (int i = 0; i < unlikeCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        likeApplicationService.unlike(userId, product.getId());
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // likeCount는 @Async AFTER_COMMIT 이벤트로 eventual consistency 반영
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                Product result = productService.getById(product.getId());
                assertThat(result.getLikeCount()).isEqualTo(likeCount);
            });
        }
    }
}
