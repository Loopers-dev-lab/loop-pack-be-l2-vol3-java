package com.loopers.domain.order;

import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.domain.product.service.ProductService;
import com.loopers.infrastructure.coupon.repository.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.repository.UserCouponJpaRepository;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.support.CouponEnums;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@DisplayName("주문 동시성 테스트")
class OrderConcurrencyTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private UserCouponJpaRepository userCouponJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        userCouponJpaRepository.deleteAll();
        couponTemplateJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @Nested
    @DisplayName("재고 동시 차감")
    class StockConcurrency {

        @Test
        @DisplayName("재고 10개 상품에 10개 스레드가 동시에 1개씩 차감하면 재고가 정확히 0이 된다")
        void concurrentStockDecrease_exactMatch() throws InterruptedException {
            // arrange
            Long productId = transactionTemplate.execute(status -> {
                Product product = productService.createProduct(1L, new ProductCommand.Create(1L, "동시성 테스트 상품", 10000, 10));
                return product.getId();
            });

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.execute(() -> {
                    try {
                        transactionTemplate.executeWithoutResult(status ->
                                productService.decreaseStockWithLock(productId, 1)
                        );
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

            // assert
            Product result = productService.findProduct(productId);
            assertThat(successCount.get()).isEqualTo(10);
            assertThat(failCount.get()).isEqualTo(0);
            assertThat(result.getStock().value()).isEqualTo(0);
        }

        @Test
        @DisplayName("재고 5개 상품에 10개 스레드가 동시에 1개씩 차감하면 5개만 성공한다")
        void concurrentStockDecrease_overStock() throws InterruptedException {
            // arrange
            Long productId = transactionTemplate.execute(status -> {
                Product product = productService.createProduct(1L, new ProductCommand.Create(1L, "재고 부족 테스트 상품", 10000, 5));
                return product.getId();
            });

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.execute(() -> {
                    try {
                        transactionTemplate.executeWithoutResult(status ->
                                productService.decreaseStockWithLock(productId, 1)
                        );
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

            // assert
            Product result = productService.findProduct(productId);
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(failCount.get()).isEqualTo(5);
            assertThat(result.getStock().value()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("쿠폰 동시 사용")
    class CouponConcurrency {

        @Test
        @DisplayName("같은 쿠폰을 2개 스레드가 동시에 사용하면 1개만 성공한다")
        void concurrentCouponUse() throws InterruptedException {
            // arrange
            Long userCouponId = transactionTemplate.execute(status -> {
                CouponTemplate template = couponService.createTemplate(new CouponCommand.CreateTemplate(
                        "동시성 테스트 쿠폰", CouponEnums.Type.FIXED, 1000, 10000,
                        LocalDateTime.now().plusDays(30)));
                UserCoupon userCoupon = couponService.issueCoupon(template.getId(), 1L);
                return userCoupon.getId();
            });

            int threadCount = 2;
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                executorService.execute(() -> {
                    try {
                        transactionTemplate.executeWithoutResult(status ->
                                couponService.useUserCoupon(userCouponId, 1L, 50000)
                        );
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

            // assert
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);
        }
    }
}
