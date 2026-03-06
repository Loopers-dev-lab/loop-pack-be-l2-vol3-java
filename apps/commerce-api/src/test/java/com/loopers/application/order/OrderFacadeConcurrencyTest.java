package com.loopers.application.order;

import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class OrderFacadeConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재고 동시성 테스트: ")
    @Nested
    class StockConcurrency {

        @DisplayName("재고 5개인 상품에 10명이 동시에 주문하면, 5건만 성공하고 재고는 0이 된다.")
        @Test
        void concurrentOrders_stockNeverGoesNegative() throws InterruptedException {
            // arrange
            int stockQuantity = 5;
            int threadCount = 10;

            Product product = productRepository.save(
                new Product(1L, "동시성 테스트 상품", 1000L, "설명", stockQuantity)
            );

            List<User> users = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                users.add(userRepository.save(new User("유저" + i, 100_000L)));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        orderFacade.createOrder(
                            users.get(index).getId(),
                            List.of(new OrderFacade.OrderItemRequest(product.getId(), 1))
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // assert
            Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();

            assertAll(
                () -> assertThat(successCount.get()).as("성공 건수").isEqualTo(stockQuantity),
                () -> assertThat(failCount.get()).as("실패 건수").isEqualTo(threadCount - stockQuantity),
                () -> assertThat(updatedProduct.getStockQuantity().value()).as("최종 재고").isEqualTo(0)
            );
        }
    }

    @DisplayName("포인트 동시성 테스트: ")
    @Nested
    class PointConcurrency {

        @DisplayName("동일 유저가 동시에 5건 주문하면, 포인트가 음수가 되지 않는다.")
        @Test
        void concurrentOrders_pointNeverGoesNegative() throws InterruptedException {
            // arrange
            int threadCount = 5;
            long pricePerItem = 1000L;
            long initialPoint = 3000L; // 3건만 성공 가능

            User user = userRepository.save(new User("포인트테스트유저", initialPoint));

            List<Product> products = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                products.add(productRepository.save(
                    new Product(1L, "상품" + i, pricePerItem, "설명", 100)
                ));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        orderFacade.createOrder(
                            user.getId(),
                            List.of(new OrderFacade.OrderItemRequest(products.get(index).getId(), 1))
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // assert
            User updatedUser = userRepository.findById(user.getId()).orElseThrow();
            long expectedSuccessCount = initialPoint / pricePerItem; // 3

            assertAll(
                () -> assertThat(successCount.get()).as("성공 건수").isEqualTo((int) expectedSuccessCount),
                () -> assertThat(failCount.get()).as("실패 건수").isEqualTo(threadCount - (int) expectedSuccessCount),
                () -> assertThat(updatedUser.getPoint()).as("최종 포인트").isGreaterThanOrEqualTo(0L)
            );
        }
    }

    @DisplayName("쿠폰 동시성 테스트: ")
    @Nested
    class CouponConcurrency {

        @DisplayName("동일 쿠폰으로 5명이 동시에 주문하면, 단 1건만 성공한다.")
        @Test
        void concurrentCouponUse_onlyOneSucceeds() throws InterruptedException {
            // arrange
            int threadCount = 5;
            CouponTemplate template = couponTemplateRepository.save(
                new CouponTemplate("1000원 할인", DiscountType.FIXED, 1000L, 0L, null, 30)
            );

            User user = userRepository.save(new User("쿠폰테스트유저", 500_000L));
            UserCoupon coupon = userCouponRepository.save(
                new UserCoupon(user.getId(), template.getId(), 30)
            );

            List<Product> products = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                products.add(productRepository.save(
                    new Product(1L, "상품" + i, 5000L, "설명", 100)
                ));
            }

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // act
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        orderFacade.createOrder(
                            user.getId(),
                            coupon.getId(),
                            List.of(new OrderFacade.OrderItemRequest(products.get(index).getId(), 1))
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            doneLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // assert
            UserCoupon usedCoupon = userCouponRepository.findById(coupon.getId()).orElseThrow();

            assertAll(
                () -> assertThat(successCount.get()).as("성공 건수").isEqualTo(1),
                () -> assertThat(failCount.get()).as("실패 건수").isEqualTo(threadCount - 1),
                () -> assertThat(usedCoupon.getStatus()).as("쿠폰 상태").isEqualTo(CouponStatus.USED)
            );
        }
    }
}
