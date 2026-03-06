package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.Stock;
import com.loopers.domain.stock.ProductStock;
import com.loopers.domain.stock.ProductStockDomainService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("쿠폰 주문 동시성 테스트")
class CouponOrderConcurrencyIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private ProductDomainService productService;

    @Autowired
    private ProductStockDomainService productStockService;

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

    @DisplayName("동일 쿠폰으로 동시 주문 시, ")
    @Nested
    class ConcurrentCouponUsage {

        @DisplayName("하나의 주문만 성공하고 나머지는 실패한다.")
        @Test
        void onlyOneOrderSucceeds_whenSameCouponUsedConcurrently() {
            int threadCount = 10;
            Long userId = 1L;

            Product product = productService.register(brandId, "에어맥스", 129000);
            productStockService.create(product.getId(), threadCount);

            Coupon coupon = couponDomainService.register("10% 할인 쿠폰", CouponType.RATE, 10, 0,
                ZonedDateTime.now().plusDays(30));
            CouponIssue couponIssue = couponIssueDomainService.issue(coupon, userId);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    try {
                        CreateOrderCommand command = new CreateOrderCommand(
                            userId,
                            List.of(new CreateOrderCommand.LineItem(product.getId(), 1)),
                            couponIssue.getId()
                        );
                        orderApplicationService.createOrder(command);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                }))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            ProductStock resultStock = productStockService.getByProductId(product.getId());
            CouponIssue resultCoupon = couponIssueDomainService.getByIdAndUserId(couponIssue.getId(), userId);

            assertAll(
                () -> assertThat(successCount.get()).isEqualTo(1),
                () -> assertThat(failCount.get()).isEqualTo(threadCount - 1),
                () -> assertThat(resultCoupon.getStatus()).isEqualTo(CouponIssueStatus.USED),
                () -> assertThat(resultStock.getStock()).isEqualTo(new Stock(threadCount - 1))
            );
        }
    }
}
