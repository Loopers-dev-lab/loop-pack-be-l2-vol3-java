package com.loopers.application;

import com.loopers.application.brand.BrandService;
import com.loopers.application.coupon.CouponIssueService;
import com.loopers.application.coupon.CouponService;
import com.loopers.application.like.LikeFacade;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductService;
import com.loopers.application.stock.StockService;
import com.loopers.domain.coupon.CouponIssueModel;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.product.Money;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyIntegrationTest {

    @Autowired private OrderFacade orderFacade;
    @Autowired private LikeFacade likeFacade;
    @Autowired private ProductFacade productFacade;
    @Autowired private BrandService brandService;
    @Autowired private ProductService productService;
    @Autowired private StockService stockService;
    @Autowired private CouponService couponService;
    @Autowired private CouponIssueService couponIssueService;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    private Long createBrand() { return brandService.register("테스트브랜드", "설명").getId(); }
    private Long createProduct(Long brandId, int stock) {
        return productFacade.register("테스트상품", "설명", new Money(10000), brandId, stock).getId();
    }

    @DisplayName("재고 동시성")
    @Nested
    class StockConcurrency {

        @DisplayName("10명이 동시에 1개씩 주문해도 재고가 정확히 차감된다")
        @Test
        void stockDecreasedCorrectlyUnderConcurrency() throws InterruptedException {
            // given
            Long brandId = createBrand();
            Long productId = createProduct(brandId, 100);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            // when
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        orderFacade.placeOrder(userId,
                            List.of(new OrderItemCommand(productId, 1)), null);
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(10);
            assertThat(stockService.getByProductId(productId).quantity()).isEqualTo(90);
        }

        @DisplayName("재고 5개인 상품에 10명이 동시 주문하면 5명만 성공한다")
        @Test
        void onlyAvailableStockSucceeds() throws InterruptedException {
            // given
            Long brandId = createBrand();
            Long productId = createProduct(brandId, 5);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            // when
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        orderFacade.placeOrder(userId,
                            List.of(new OrderItemCommand(productId, 1)), null);
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(stockService.getByProductId(productId).quantity()).isEqualTo(0);
        }
    }

    @DisplayName("쿠폰 동시성")
    @Nested
    class CouponConcurrency {

        @DisplayName("동일 쿠폰으로 여러 기기에서 동시 주문해도 1번만 사용된다")
        @Test
        void couponUsedOnlyOnce() throws InterruptedException {
            // given
            Long brandId = createBrand();
            Long productId = createProduct(brandId, 100);
            CouponModel coupon = couponService.create("테스트쿠폰", CouponType.FIXED, 1000,
                null, LocalDateTime.of(2099, 12, 31, 23, 59));
            Long userId = 1L;
            CouponIssueModel issue = couponIssueService.issue(coupon, userId);

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        orderFacade.placeOrder(userId,
                            List.of(new OrderItemCommand(productId, 1)), issue.getId());
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(1);
        }
    }

    @DisplayName("좋아요 동시성")
    @Nested
    class LikeConcurrency {

        @DisplayName("10명이 동시에 좋아요해도 좋아요 수가 정확하다")
        @Test
        void likeCountAccurateUnderConcurrency() throws InterruptedException {
            // given
            Long brandId = createBrand();
            Long productId = createProduct(brandId, 10);

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger();

            // when
            for (int i = 0; i < threadCount; i++) {
                long userId = i + 1;
                executor.submit(() -> {
                    try {
                        likeFacade.like(userId, productId);
                        successCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(10);
            assertThat(productService.getProduct(productId).likeCount()).isEqualTo(10);
        }
    }
}
