package com.loopers.domain.order;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class OrderConcurrencyTest {

    private static final Long USER_ID = 1L;
    private static final int THREAD_COUNT = 10;
    private static final Money VALID_PRICE = new Money(10000);

    @Autowired
    private OrderFacade orderFacade;

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

    @DisplayName("동시 주문으로 인한 재고 차감 시")
    @Nested
    class ConcurrentStockDecrement {

        @DisplayName("재고보다 많은 동시 주문이 들어오면, 재고 수만큼만 성공하고 재고는 0이 된다.")
        @Test
        void onlyStockCountSucceeds_whenOrdersExceedStock() throws InterruptedException {
            // arrange: 재고 10개, 11명 동시 주문 1개씩
            final int STOCK_COUNT = THREAD_COUNT;
            final int OVER_THREAD_COUNT = THREAD_COUNT + 1;
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, new Stock(STOCK_COUNT)));
            OrderCreateCommand command = new OrderCreateCommand(
                    List.of(new OrderCreateCommand.Item(product.getId(), 1)),
                    null
            );

            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(OVER_THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(OVER_THREAD_COUNT);

            // act
            for (int i = 0; i < OVER_THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드를 대기시키고 동시에 출발
                        orderFacade.create(USER_ID, command);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 예상된 실패 (재고 부족 - BAD_REQUEST)
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            startLatch.countDown(); // 모든 스레드 동시 출발
            doneLatch.await();
            executor.shutdown();

            // assert
            assertThat(successCount.get()).isEqualTo(STOCK_COUNT);
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock().getQuantity()).isZero();
        }

        @DisplayName("재고 내에서 동시 주문이 들어오면, 모두 성공하고 재고가 정확히 차감된다.")
        @Test
        void allSucceedAndStockIsAccurate_whenOrdersWithinStock() throws InterruptedException {
            // arrange: 재고 20개, 10명 동시 2개씩 주문
            final int ORDER_QUANTITY = 2;
            final int INITIAL_STOCK = THREAD_COUNT * ORDER_QUANTITY;
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", VALID_PRICE, new Stock(INITIAL_STOCK)));
            OrderCreateCommand command = new OrderCreateCommand(
                    List.of(new OrderCreateCommand.Item(product.getId(), ORDER_QUANTITY)),
                    null
            );

            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

            // act
            for (int i = 0; i < THREAD_COUNT; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // 모든 스레드를 대기시키고 동시에 출발
                        orderFacade.create(USER_ID, command);
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

            // assert
            assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
            Product updated = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(updated.getStock().getQuantity()).isZero();
        }
    }
}
