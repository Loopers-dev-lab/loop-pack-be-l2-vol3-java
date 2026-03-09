package com.loopers.domain.order;

import com.loopers.application.order.CreateOrderItemParam;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문·재고 동시성 테스트.
 * 동일 상품에 대해 여러 주문이 동시에 요청되어도 재고가 정상 차감되고 0 미만으로 내려가지 않는지 검증. (05-transaction-query §12.3)
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class OrderStockConcurrencyIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;
    @Autowired
    private ProductService productService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private static final int INITIAL_STOCK = 5;
    private static final int CONCURRENT_THREADS = 10;

    @BeforeEach
    void setUp() {
        Long brandId = brandService.registerBrand("브랜드").getId();
        ProductModel product = productService.registerProduct(brandId, "상품", new BigDecimal("10000"), INITIAL_STOCK);
        productId = product.getId();
        userId = 1L;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동시에 주문해도 재고가 정상적으로 차감되고 0 미만으로 내려가지 않는다.")
    @Test
    void concurrency_stockDecreasedCorrectlyWhenConcurrentOrders() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    orderFacade.placeOrder(
                            userId,
                            List.of(new CreateOrderItemParam(productId, 1, null)),
                            null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        Optional<ProductModel> product = productService.findById(productId);
        assertThat(product).isPresent();
        int remainingStock = product.get().getStockQuantity();

        // 재고 불변식: 성공 건수 + 남은 재고 = 초기 재고. 동시성으로 5성공/5실패가 정확히 나오지 않을 수 있음(타이밍).
        assertThat(remainingStock).as("재고는 0 미만이면 안 됨(초과 판매 방지)").isGreaterThanOrEqualTo(0);
        assertThat(successCount.get() + remainingStock).as("성공 건수 + 남은 재고 = 초기 재고").isEqualTo(INITIAL_STOCK);
        assertThat(successCount.get()).as("성공 건수는 초기 재고를 넘을 수 없음").isLessThanOrEqualTo(INITIAL_STOCK);
    }
}
