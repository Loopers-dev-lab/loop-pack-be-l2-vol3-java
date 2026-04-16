package com.loopers.application.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.queue.QueueCacheService;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderConcurrencyTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private CouponTemplateJpaRepository couponTemplateJpaRepository;

    @Autowired
    private IssuedCouponJpaRepository issuedCouponJpaRepository;

    @Autowired
    private QueueCacheService queueCacheService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String RAW_PASSWORD = "Test1234!";

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("동일 쿠폰 동시 사용")
    @Nested
    class CouponConcurrencyTest {

        @DisplayName("2개의 스레드가 같은 쿠폰으로 동시에 주문하면, 1건만 성공하고 쿠폰 상태는 USED가 된다.")
        @Test
        void onlyOneOrderSucceeds_whenTwoThreadsUseSameCouponConcurrently() throws InterruptedException {
            // Arrange
            Brand brand = brandJpaRepository.save(new Brand("브랜드", "설명"));
            Product product = productJpaRepository.save(
                new Product(brand.getId(), "상품", 10000, 100, "설명", null)
            );
            CouponTemplate template = couponTemplateJpaRepository.save(
                new CouponTemplate("할인쿠폰", CouponType.FIXED, 1000, null, ZonedDateTime.now().plusDays(30))
            );

            String encodedPassword = passwordEncoder.encode(RAW_PASSWORD);
            User user = new User("couponuser", encodedPassword, "쿠폰유저", "19900101", "couponuser@test.com");
            user.restoreBalance(100_000L);
            User savedUser = userJpaRepository.save(user);

            IssuedCoupon issuedCoupon = issuedCouponJpaRepository.save(
                new IssuedCoupon(savedUser.getId(), template.getId())
            );
            String entryToken = queueCacheService.issueToken(savedUser.getId());

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    latch.countDown();
                    try {
                        latch.await();
                        orderFacade.createOrder(
                            "couponuser", RAW_PASSWORD, entryToken,
                            List.of(new OrderRequest.OrderItemRequest(product.getId(), 1)),
                            issuedCoupon.getId()
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            // Assert
            IssuedCoupon found = issuedCouponJpaRepository.findById(issuedCoupon.getId()).orElseThrow();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(1);
            assertThat(found.getStatus()).isEqualTo(IssuedCouponStatus.USED);
        }
    }

    @DisplayName("재고 동시 차감")
    @Nested
    class StockConcurrencyTest {

        @DisplayName("재고가 5개인 상품에 10명이 동시에 1개씩 주문하면, 5건만 성공하고 재고는 0이 된다.")
        @Test
        void onlyFiveOrdersSucceed_whenTenUsersOrderProductWithStockOfFiveConcurrently() throws InterruptedException {
            // Arrange
            Brand brand = brandJpaRepository.save(new Brand("브랜드2", "설명"));
            Product product = productJpaRepository.save(
                new Product(brand.getId(), "재고상품", 1000, 5, "설명", null)
            );

            int threadCount = 10;
            String encodedPassword = passwordEncoder.encode(RAW_PASSWORD);
            List<String> loginIds = new ArrayList<>();
            List<String> entryTokens = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                String loginId = "stockuser" + i;
                User user = new User(loginId, encodedPassword, "재고유저" + i, "19900101", "stock" + i + "@test.com");
                user.restoreBalance(100_000L);
                User savedUser = userJpaRepository.save(user);
                loginIds.add(loginId);
                entryTokens.add(queueCacheService.issueToken(savedUser.getId()));
            }

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            // Act
            for (int i = 0; i < threadCount; i++) {
                final String loginId = loginIds.get(i);
                final String entryToken = entryTokens.get(i);
                executor.submit(() -> {
                    latch.countDown();
                    try {
                        latch.await();
                        orderFacade.createOrder(
                            loginId, RAW_PASSWORD, entryToken,
                            List.of(new OrderRequest.OrderItemRequest(product.getId(), 1)),
                            null
                        );
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);

            // Assert
            Product found = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(successCount.get()).isEqualTo(5);
            assertThat(found.getStock()).isEqualTo(0);
        }
    }
}
