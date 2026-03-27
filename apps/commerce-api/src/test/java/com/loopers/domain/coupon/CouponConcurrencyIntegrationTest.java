package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.application.order.CreateOrderItemParam;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.BrandService;
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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 쿠폰 동시성 테스트.
 * 동일 발급 쿠폰으로 여러 기기에서 동시에 주문해도 쿠폰이 단 한 번만 사용되는지 검증. (05-transaction-query §12.3)
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class CouponConcurrencyIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;
    @Autowired
    private CouponService couponService;
    @Autowired
    private CouponFacade couponFacade;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private Long issuedCouponId;

    @BeforeEach
    void setUp() {
        Long brandId = brandService.registerBrand("브랜드").getId();
        productId = productService.registerProduct(brandId, "상품", new BigDecimal("50000"), 100).getId();
        CouponTemplateInfo template = couponFacade.registerTemplate(
                "동시성테스트 쿠폰", "FIXED", 1000, BigDecimal.valueOf(10000),
                ZonedDateTime.now().plusDays(30), null);
        userId = 1L;
        issuedCouponId = couponFacade.issueCoupon(userId, template.id()).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 쿠폰으로 여러 요청이 동시에 주문해도 쿠폰은 단 한 번만 사용된다.")
    @Test
    void concurrency_sameCouponUsedByMultipleRequests_onlyOneSucceeds() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    orderFacade.placeOrder(
                            userId,
                            List.of(new CreateOrderItemParam(productId, 1, null)),
                            issuedCouponId);
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

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        var myCoupons = couponService.findByUserId(userId, PageRequest.of(0, 10)).getContent();
        assertThat(myCoupons).hasSize(1);
        assertThat(myCoupons.get(0).getStatus()).isEqualTo(IssuedCouponStatus.USED);
    }
}
