package com.loopers.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.application.order.AdminOrderService;
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

@DisplayName("재고 동시 차감 동시성 테스트")
@SpringBootTest
class StockDeductionConcurrencyTest {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private AdminOrderService adminOrderService;

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

    @DisplayName("재고 10인 상품에 여러 건 동시 주문 → 재고 정합성 유지, 초과 주문 실패")
    @RepeatedTest(3)
    void concurrentOrder_sameProduct_stockConsistency() throws InterruptedException {
        // arrange
        int initialStock = 10;
        BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드B"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create(brand.getId(), "한정 상품", 10000, initialStock));

        List<UserModel> users = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            users.add(createUserWithPoint("stockuser" + i, 1_000_000));
        }

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        // act
        for (int i = 0; i < THREAD_COUNT; i++) {
            final UserModel user = users.get(i);
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    adminOrderService.createOrder(user.getId(),
                            new OrderCriteria.Create(List.of(
                                    new OrderCriteria.Create.CreateItem(
                                            product.getId(), 1, 10000))));
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
        ProductModel updated = productJpaRepository.findById(
                product.getId()).orElseThrow();

        System.out.println("\n========== 동일 상품 동시 주문 재고 차감 ==========");
        System.out.println("  초기 재고(initialStock): " + initialStock);
        System.out.println("  요청 수(threadCount)   : " + THREAD_COUNT);
        System.out.println("  성공 카운트            : " + successCount.get());
        System.out.println("  실패 카운트            : " + failCount.get());
        System.out.println("  남은 재고              : " + updated.getStock());
        System.out.println("  재고 정합성            : " + (updated.getStock() == initialStock - successCount.get() ? "OK" : "MISMATCH"));
        System.out.println("  초과 차감 여부          : " + (updated.getStock() < 0 ? "OVER-DEDUCTED" : "OK"));
        System.out.println("================================================\n");

        assertAll(
                () -> assertThat(successCount.get())
                        .as("재고 수만큼만 성공해야 한다")
                        .isEqualTo(initialStock),
                () -> assertThat(updated.getStock())
                        .as("재고는 0이어야 한다")
                        .isEqualTo(0),
                () -> assertThat(updated.getStock())
                        .as("재고가 음수가 되면 안 된다")
                        .isGreaterThanOrEqualTo(0));
    }
}
