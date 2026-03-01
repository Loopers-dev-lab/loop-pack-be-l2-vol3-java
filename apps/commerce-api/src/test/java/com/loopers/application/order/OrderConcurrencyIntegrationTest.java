package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
@DisplayName("주문 동시성 테스트")
class OrderConcurrencyIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

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

    @DisplayName("동일 상품 동시 주문 시, ")
    @Nested
    class ConcurrentStockDeduction {

        @DisplayName("재고가 충분하면, 모든 주문이 성공하고 최종 재고가 0이 된다.")
        @Test
        void allOrdersSucceed_whenStockIsSufficient() throws InterruptedException {
            int threadCount = 10;
            Product product = productService.register(brandId, "에어맥스", 129000, threadCount);

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        CreateOrderCommand command = new CreateOrderCommand(
                            userId,
                            List.of(new CreateOrderCommand.LineItem(product.getId(), 1))
                        );
                        orderApplicationService.createOrder(command);
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

            Product result = productService.getById(product.getId());
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failCount.get()).isEqualTo(0);
            assertThat(result.getStock()).isEqualTo(new Stock(0));
        }

        @DisplayName("재고가 부족하면, 재고만큼만 성공하고 나머지는 실패한다.")
        @Test
        void onlyAvailableStockSucceeds_whenStockIsInsufficient() throws InterruptedException {
            int threadCount = 10;
            int availableStock = 5;
            Product product = productService.register(brandId, "에어맥스", 129000, availableStock);

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executorService.submit(() -> {
                    try {
                        CreateOrderCommand command = new CreateOrderCommand(
                            userId,
                            List.of(new CreateOrderCommand.LineItem(product.getId(), 1))
                        );
                        orderApplicationService.createOrder(command);
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

            Product result = productService.getById(product.getId());
            assertThat(successCount.get()).isEqualTo(availableStock);
            assertThat(failCount.get()).isEqualTo(threadCount - availableStock);
            assertThat(result.getStock()).isEqualTo(new Stock(0));
        }
    }
}
