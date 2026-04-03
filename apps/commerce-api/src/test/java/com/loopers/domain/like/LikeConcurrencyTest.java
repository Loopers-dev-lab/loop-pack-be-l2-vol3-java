package com.loopers.domain.like;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
public class LikeConcurrencyTest {

    private static final int THREAD_COUNT = 10;
    private static final Long USER_ID = 1L;
    private static final Money VALID_PRICE = new Money(10000);
    private static final Stock VALID_STOCK = new Stock(100);

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeFacade likeFacade;

    @Autowired
    private LikeJpaRepository likeJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 사용자가 같은 상품에 동시에 좋아요 등록 시")
    @Nested
    class SameUserConcurrentLike {

        @DisplayName("동시에 여러 요청이 들어와도 좋아요는 1번만 등록된다.")
        @Test
        void onlyOneLikeCreated_whenSameUserRequestsConcurrently() throws InterruptedException {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, VALID_STOCK));

            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드를 대기시키고 동시에 출발
                        likeService.create(USER_ID, product.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 예상된 실패 (TOCTOU 경쟁 또는 exists 체크에서 CONFLICT)
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown(); // 모든 스레드 동시 출발
            doneLatch.await();
            executor.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(likeJpaRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).hasSize(1);
        }
    }

    @DisplayName("서로 다른 사용자가 같은 상품에 동시에 좋아요 등록 시")
    @Nested
    class DifferentUsersConcurrentLike {

        @DisplayName("모든 요청이 성공하고 비동기 이벤트를 통해 좋아요 수가 정확하게 증가한다.")
        @Test
        void allLikesCreatedAndLikeCountIsAccurate_whenDifferentUsersConcurrently() throws InterruptedException {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, VALID_STOCK));

            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                final long userId = i + 1L; // 서로 다른 userId (1~10)
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드를 대기시키고 동시에 출발
                        likeFacade.create(userId, product.getId());
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 예상치 못한 실패
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown(); // 모든 스레드 동시 출발
            doneLatch.await();
            executor.shutdown();

            // assert - 모든 좋아요 등록 성공
            assertThat(successCount.get()).isEqualTo(THREAD_COUNT);

            // assert - 비동기 이벤트 처리 완료 후 likeCount 검증
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
                assertThat(updated.getLikeCount()).isEqualTo(THREAD_COUNT);
            });
        }
    }
}
