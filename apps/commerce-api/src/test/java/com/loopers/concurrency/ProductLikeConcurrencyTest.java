package com.loopers.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.application.product.ProductLikeFacade;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductLikeJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("좋아요 동시성 테스트")
@SpringBootTest
class ProductLikeConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private ProductLikeFacade productLikeFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductLikeJpaRepository productLikeJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("같은 유저가 같은 상품에 동시 좋아요 → 1건만 성공, 나머지 CONFLICT 실패")
    @RepeatedTest(3)
    void concurrentLike_sameUserSameProduct_onlyOneSucceeds() throws InterruptedException {
        // arrange
        UserModel user = userJpaRepository.save(
                UserModel.create("testuser", "encrypted", "테스트",
                        LocalDate.of(1990, 1, 1), "testuser@test.com"));
        BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드A"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create(brand.getId(), "상품A", 10000, 100));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    productLikeFacade.like(user.getId(), product.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // assert
        long likeCount = productLikeJpaRepository.countByProductId(product.getId());

        System.out.println("\n========== 동일 유저 동시 좋아요 ==========");
        System.out.println("  요청 수(threadCount)   : " + THREAD_COUNT);
        System.out.println("  성공 카운트            : " + successCount.get());
        System.out.println("  실패 카운트            : " + failCount.get());
        System.out.println("  DB 좋아요 레코드 수    : " + likeCount);
        System.out.println("  중복 등록 여부          : " + (likeCount > 1 ? "DUPLICATED" : "OK"));
        System.out.println("==========================================\n");

        assertAll(
                () -> assertThat(successCount.get())
                        .as("정확히 1건만 성공해야 한다")
                        .isEqualTo(1),
                () -> assertThat(failCount.get())
                        .as("나머지는 실패해야 한다")
                        .isEqualTo(THREAD_COUNT - 1),
                () -> assertThat(likeCount)
                        .as("DB에 좋아요 레코드는 1건만 존재해야 한다")
                        .isEqualTo(1));
    }
}
