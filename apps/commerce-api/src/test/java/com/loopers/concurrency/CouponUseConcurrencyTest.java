package com.loopers.concurrency;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.*;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest
class CouponUseConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponIssueRepository couponIssueRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 쿠폰으로 여러 기기에서 동시 주문하면 단 한 건만 성공한다")
    @Test
    void concurrentOrdersWithSameCoupon_onlyOneSucceeds() throws InterruptedException {
        // arrange
        int threadCount = 10;
        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));
        Product product = productRepository.save(
            new Product(brand.getId(), "에어맥스", new Price(100000), new Stock(100)));
        Coupon coupon = couponRepository.save(
            new Coupon("5000원 할인", DiscountType.FIXED, 5000, 0,
                ZonedDateTime.now().plusDays(30)));
        CouponIssue couponIssue = couponIssueRepository.save(
            new CouponIssue(coupon.getId(), 1L, coupon.getExpiredAt()));

        Long productId = product.getId();
        Long couponIssueId = couponIssue.getId();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act: 같은 memberId, 같은 couponIssueId로 동시 주문
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderFacade.createOrder(1L,
                        List.of(new OrderFacade.OrderItemRequest(productId, 1)),
                        couponIssueId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        // assert
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        CouponIssue reloaded = couponIssueRepository.findById(couponIssueId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CouponIssueStatus.USED);

        Product reloadedProduct = productRepository.findById(productId).orElseThrow();
        assertThat(reloadedProduct.getStock().getQuantity()).isEqualTo(99);
    }
}
