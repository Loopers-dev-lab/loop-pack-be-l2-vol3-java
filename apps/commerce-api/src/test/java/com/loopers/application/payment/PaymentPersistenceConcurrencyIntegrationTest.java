package com.loopers.application.payment;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 역할: 동일 주문에 대한 동시 {@code savePending} 호출 시 PENDING이 한 건만 생기거나
 * 나머지는 충돌로 막히는지(멱등·동시성) 검증한다 (06 §2.1).
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class PaymentPersistenceConcurrencyIntegrationTest {

    private static final String CB = "http://localhost:8080/cb";

    @Autowired
    private PaymentPersistenceService persistenceService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동일 주문에 동시 savePending 호출 시 하나만 성공하고 나머지는 CONFLICT다.")
    void savePending_concurrentSameOrder_onlyOneSucceedsOthersConflict() throws InterruptedException {
        // given
        Long brandId = brandService.registerBrand("conc-brand").getId();
        ProductModel product = productService.registerProduct(brandId, "p", new BigDecimal("5000"), 20);
        OrderModel order = orderService.create(1L, List.of(
                new ProductValidationRequest(product.getId(), Quantity.of(1), null)));
        Long orderId = order.getId();

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger conflict = new AtomicInteger(0);

        // when (동시성으로 savePending 호출을 유발)
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    persistenceService.savePendingAndGetRequestParam(
                            1L, orderId, "SAMSUNG", "1111", CB);
                    success.incrementAndGet();
                } catch (CoreException e) {
                    if (e.getErrorType() == ErrorType.CONFLICT) {
                        conflict.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        // then
        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(threads - 1);
    }
}
