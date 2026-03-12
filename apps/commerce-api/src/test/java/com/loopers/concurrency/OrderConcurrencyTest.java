package com.loopers.concurrency;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderItemRequest;
import com.loopers.application.order.PlaceOrderWithDiscountRequest;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.coupon.IssuedCouponStatus;
import com.loopers.domain.point.UserPoint;
import com.loopers.domain.point.UserPointRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
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
@DisplayName("주문 동시성 테스트")
class OrderConcurrencyTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private UserPointRepository userPointRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("동시에 재고를 차감해도 정합성이 유지된다")
    void 동시_재고_차감_정합성() throws InterruptedException {
        // Arrange: 재고 10개 상품
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(10), "http://image.url")
        );

        int threadCount = 20;  // 20명이 동시에 1개씩 주문
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1;
            executor.submit(() -> {
                try {
                    orderApplicationService.placeOrder(userId, List.of(
                        new OrderItemRequest(product.getId(), 1)
                    ));
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

        // Assert: 10개만 성공, 나머지 10개는 재고 부족으로 실패
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        // 재고가 0인지 확인
        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStock().quantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("같은 쿠폰을 동시에 사용해도 한 번만 사용된다")
    void 동시_쿠폰_사용_중복_방지() throws InterruptedException {
        // Arrange: 상품과 쿠폰 설정
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(50000), new Stock(100), "http://image.url")
        );

        CouponTemplate template = couponTemplateRepository.save(
            CouponTemplate.createFixed("5000원 할인", new Money(5000), Money.ZERO, 100, ZonedDateTime.now().plusDays(7))
        );

        // 사용자 1에게 쿠폰 발급
        Long userId = 1L;
        IssuedCoupon coupon = issuedCouponRepository.save(IssuedCoupon.create(userId, template));

        int threadCount = 10;  // 10번 동시 사용 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderApplicationService.placeOrderWithDiscount(userId,
                        new PlaceOrderWithDiscountRequest(
                            List.of(new OrderItemRequest(product.getId(), 1)),
                            coupon.getId(),
                            null
                        )
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
        executor.shutdown();

        // Assert: 정확히 1번만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // 쿠폰 상태 확인
        IssuedCoupon used = issuedCouponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(used.getStatus()).isEqualTo(IssuedCouponStatus.USED);
    }

    @Test
    @DisplayName("동시에 포인트를 사용해도 정합성이 유지된다")
    void 동시_포인트_차감_정합성() throws InterruptedException {
        // Arrange: 10000 포인트 설정
        Long userId = 1L;
        userPointRepository.save(UserPoint.create(userId, 10000));

        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(5000), new Stock(100), "http://image.url")
        );

        int threadCount = 10;  // 10번 동시에 1000포인트씩 사용 시도
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderApplicationService.placeOrderWithDiscount(userId,
                        new PlaceOrderWithDiscountRequest(
                            List.of(new OrderItemRequest(product.getId(), 1)),
                            null,
                            1000L  // 1000 포인트 사용
                        )
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
        executor.shutdown();

        // Assert: 10번 모두 성공 (10000 포인트 → 각 1000씩 10번)
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);

        // 포인트가 0인지 확인
        UserPoint updated = userPointRepository.findByUserId(userId).orElseThrow();
        assertThat(updated.getBalance()).isEqualTo(0);
    }

    @Test
    @DisplayName("주문 실패 시 쿠폰이 롤백된다")
    void 주문_실패_시_쿠폰_롤백() throws InterruptedException {
        // Arrange: 재고 0개 상품과 쿠폰 설정
        Product product = productRepository.save(
            Product.create(1L, "품절 상품", "설명", new Money(50000), new Stock(0), "http://image.url")
        );

        CouponTemplate template = couponTemplateRepository.save(
            CouponTemplate.createFixed("5000원 할인", new Money(5000), Money.ZERO, 100, ZonedDateTime.now().plusDays(7))
        );

        Long userId = 1L;
        IssuedCoupon coupon = issuedCouponRepository.save(IssuedCoupon.create(userId, template));

        // Act: 재고 부족으로 실패해야 함
        try {
            orderApplicationService.placeOrderWithDiscount(userId,
                new PlaceOrderWithDiscountRequest(
                    List.of(new OrderItemRequest(product.getId(), 1)),
                    coupon.getId(),
                    null
                )
            );
        } catch (Exception ignored) {
        }

        // Assert: 쿠폰이 AVAILABLE 상태로 유지
        IssuedCoupon unchanged = issuedCouponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("주문 실패 시 포인트가 롤백된다")
    void 주문_실패_시_포인트_롤백() throws InterruptedException {
        // Arrange: 포인트와 품절 상품
        Long userId = 1L;
        userPointRepository.save(UserPoint.create(userId, 10000));

        Product product = productRepository.save(
            Product.create(1L, "품절 상품", "설명", new Money(50000), new Stock(0), "http://image.url")
        );

        // Act: 재고 부족으로 실패해야 함
        try {
            orderApplicationService.placeOrderWithDiscount(userId,
                new PlaceOrderWithDiscountRequest(
                    List.of(new OrderItemRequest(product.getId(), 1)),
                    null,
                    5000L
                )
            );
        } catch (Exception ignored) {
        }

        // Assert: 포인트가 원래대로 유지
        UserPoint unchanged = userPointRepository.findByUserId(userId).orElseThrow();
        assertThat(unchanged.getBalance()).isEqualTo(10000);
    }
}
