package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.Order;
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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("주문 취소 동시성 테스트")
class OrderCancelConcurrencyIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private BrandDomainService brandService;

    @Autowired
    private CouponDomainService couponDomainService;

    @Autowired
    private CouponIssueDomainService couponIssueDomainService;

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

    @DisplayName("동일 주문에 동시 취소 시, ")
    @Nested
    class ConcurrentCancel {

        @DisplayName("하나의 취소만 성공하고 재고와 쿠폰이 정확히 복원된다.")
        @Test
        void onlyCancelSucceedsOnce_whenConcurrent() throws InterruptedException {
            int threadCount = 10;
            int initialStock = 10;
            Long userId = 1L;

            Product product = productService.register(brandId, "에어맥스", 129000, initialStock);

            Coupon coupon = couponDomainService.register("10% 할인 쿠폰", CouponType.RATE, 10, 0,
                ZonedDateTime.now().plusDays(30));
            CouponIssue couponIssue = couponIssueDomainService.issue(coupon, userId);

            CreateOrderCommand command = new CreateOrderCommand(
                userId,
                List.of(new CreateOrderCommand.LineItem(product.getId(), 1)),
                couponIssue.getId()
            );
            Order order = orderApplicationService.createOrder(command);

            // 주문 후 재고 9 확인
            assertThat(productService.getById(product.getId()).getStock()).isEqualTo(new Stock(initialStock - 1));

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        orderApplicationService.cancelOrder(userId, order.getId());
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
            executorService.shutdown();

            Product resultProduct = productService.getById(product.getId());
            CouponIssue resultCoupon = couponIssueDomainService.getByIdAndUserId(couponIssue.getId(), userId);

            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(failCount.get()).isEqualTo(threadCount - 1),
                () -> assertThat(resultProduct.getStock()).isEqualTo(new Stock(initialStock)),
                () -> assertThat(resultCoupon.getStatus()).isEqualTo(CouponIssueStatus.AVAILABLE)
            );
        }
    }
}
