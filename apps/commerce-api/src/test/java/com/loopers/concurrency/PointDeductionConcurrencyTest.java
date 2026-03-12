package com.loopers.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@DisplayName("포인트 동시 차감 동시성 테스트")
@SpringBootTest
class PointDeductionConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

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

    private UserModel createUserWithPoint(String loginId, long point) {
        UserModel user = UserModel.create(
                loginId, "encrypted", "테스트", java.time.LocalDate.of(1990, 1, 1),
                loginId + "@test.com");
        user.addPoint(point);
        return userJpaRepository.save(user);
    }

    private ProductModel createProduct(Long brandId, String name, int price, int stock) {
        return productJpaRepository.save(
                ProductModel.create(brandId, name, price, stock));
    }

    @DisplayName("같은 유저로 동시 주문 시 포인트 정합성이 유지된다")
    @RepeatedTest(3)
    void concurrentOrder_sameUser_pointConsistency() throws InterruptedException {
        // arrange
        int initialPoint = 100_000;
        int productPrice = 40_000;
        int initialStock = 100;
        int maxExpectedSuccess = initialPoint / productPrice; // 2

        UserModel user = createUserWithPoint("pointuser", initialPoint);
        BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드P"));
        ProductModel product = createProduct(
                brand.getId(), "포인트테스트상품", productPrice, initialStock);

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        // act
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    orderFacade.createOrder(user.getId(),
                            new OrderCriteria.Create(List.of(
                                    new OrderCriteria.Create.CreateItem(
                                            product.getId(), 1, productPrice))));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e.getMessage());
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
        UserModel updatedUser = userJpaRepository.findById(
                user.getId()).orElseThrow();
        ProductModel updatedProduct = productJpaRepository.findById(
                product.getId()).orElseThrow();
        long expectedPoint = initialPoint - ((long) successCount.get() * productPrice);

        System.out.println("\n========== 동일 유저 포인트 동시 차감 ==========");
        System.out.println("  초기 포인트            : " + initialPoint);
        System.out.println("  상품 가격              : " + productPrice);
        System.out.println("  최대 성공 가능 수       : " + maxExpectedSuccess);
        System.out.println("  요청 수(threadCount)   : " + THREAD_COUNT);
        System.out.println("  성공 카운트            : " + successCount.get());
        System.out.println("  실패 카운트            : " + failCount.get());
        System.out.println("  최종 포인트            : " + updatedUser.getPoint());
        System.out.println("  기대 포인트            : " + expectedPoint);
        System.out.println("  남은 재고              : " + updatedProduct.getStock());
        System.out.println("  포인트 정합성           : " + (updatedUser.getPoint() == expectedPoint ? "OK" : "MISMATCH"));
        System.out.println("  포인트 음수 여부        : " + (updatedUser.getPoint() < 0 ? "NEGATIVE" : "OK"));
        System.out.println("===============================================\n");

        assertAll(
                () -> assertThat(successCount.get())
                        .as("최대 %d건만 성공해야 한다", maxExpectedSuccess)
                        .isLessThanOrEqualTo(maxExpectedSuccess),
                () -> assertThat(updatedUser.getPoint())
                        .as("포인트가 음수가 되면 안 된다")
                        .isGreaterThanOrEqualTo(0),
                () -> assertThat(updatedUser.getPoint())
                        .as("포인트 정합성이 유지되어야 한다")
                        .isEqualTo(expectedPoint),
                () -> assertThat(updatedProduct.getStock())
                        .as("재고 정합성이 유지되어야 한다")
                        .isEqualTo(initialStock - successCount.get()));
    }
}
