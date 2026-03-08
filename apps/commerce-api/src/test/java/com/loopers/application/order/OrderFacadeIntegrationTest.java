package com.loopers.application.order;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponDto;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.OrderStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.user.Gender;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderFacadeIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        UserDto.UserInfo user = userFacade.register(
            "orderuser1", "TestPass1!", "주문테스터",
            LocalDate.of(2000, 1, 1), "order1@loopers.com", Gender.MALE
        );
        userId = user.id();

        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("ORDER_BRAND"), new BrandDescription("주문 브랜드"))
        );

        Product product = productJpaRepository.save(
            Product.create(brand.getId(), "주문 상품", "설명", BigDecimal.valueOf(25000), 5)
        );
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("주문 생성 시 재고가 차감되고 스냅샷 정보가 저장된다")
    @Test
    void placeOrder_success() {
        OrderDto.OrderInfo result = orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 2)),
            null
        );

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().productId()).isEqualTo(productId);
        assertThat(result.items().getFirst().productName()).isEqualTo("주문 상품");
        assertThat(result.totalAmount()).isEqualByComparingTo("50000");

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(3);
    }

    @DisplayName("재고가 부족하면 주문 생성에 실패하고 재고는 유지된다")
    @Test
    void placeOrder_fail_notEnoughStock() {
        assertThatThrownBy(() ->
            orderFacade.placeOrder(
                userId,
                List.of(new OrderDto.OrderLineCommand(productId, 6)),
                null
            )
        )
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.PRODUCT_INSUFFICIENT_STOCK));

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(5);
    }

    @DisplayName("사용자 주문 상세 조회 시 본인 주문을 조회할 수 있다")
    @Test
    void getOrder_success() {
        OrderDto.OrderInfo created = orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1)),
            null
        );

        OrderDto.OrderInfo found = orderFacade.getOrder(userId, created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.items()).hasSize(1);
        assertThat(found.totalAmount()).isEqualByComparingTo("25000");
    }

    @DisplayName("존재하지 않는 주문 상세 조회 시 예외가 발생한다")
    @Test
    void getOrder_fail_notFound() {
        assertThatThrownBy(() -> orderFacade.getOrder(userId, 9999L))
            .isInstanceOf(CoreException.class)
            .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.ORDER_NOT_FOUND));
    }

    @DisplayName("기간 내 주문 목록 조회가 가능하다")
    @Test
    void getOrders_success() {
        orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1)),
            null
        );

        ZonedDateTime startAt = ZonedDateTime.now().minusDays(1);
        ZonedDateTime endAt = ZonedDateTime.now().plusDays(1);
        List<OrderDto.OrderInfo> orders = orderFacade.getOrders(userId, startAt, endAt);

        assertThat(orders).hasSize(1);
    }

    @DisplayName("주문 취소 시 상태가 CANCELLED로 변경되고 재고가 복구된다")
    @Test
    void cancelOrder_success() {
        OrderDto.OrderInfo created = orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 2)),
            null
        );

        OrderDto.OrderInfo cancelled = orderFacade.cancelOrder(userId, created.id());

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(5);
    }

    @DisplayName("이미 취소된 주문을 다시 취소해도 멱등하게 성공한다")
    @Test
    void cancelOrder_idempotent() {
        OrderDto.OrderInfo created = orderFacade.placeOrder(
            userId,
            List.of(new OrderDto.OrderLineCommand(productId, 1)),
            null
        );
        orderFacade.cancelOrder(userId, created.id());

        OrderDto.OrderInfo cancelledAgain = orderFacade.cancelOrder(userId, created.id());

        assertThat(cancelledAgain.status()).isEqualTo(OrderStatus.CANCELLED);
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(5);
    }

    @DisplayName("동일한 상품에 여러 주문이 동시에 요청되어도 재고가 정상적으로 차감된다")
    @Test
    void placeOrder_concurrent_stockDeduction() throws InterruptedException {
        int concurrency = 3;

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderFacade.placeOrder(
                        userId,
                        List.of(new OrderDto.OrderLineCommand(productId, 1)),
                        null
                    );
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(failures).isEmpty();
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(5 - concurrency);
    }

    @DisplayName("재고 2개 상품에 5명이 동시에 주문하면 정확히 2건만 성공하고 재고는 0이 된다")
    @Test
    void placeOrder_concurrent_stockExhausted() throws InterruptedException {
        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("LIMITED_BRAND"), new BrandDescription("한정 브랜드"))
        );
        Product limitedProduct = productJpaRepository.save(
            Product.create(brand.getId(), "한정 상품", "설명", BigDecimal.valueOf(10000), 2)
        );
        Long limitedProductId = limitedProduct.getId();

        int concurrency = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        List<Long> successIds = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    OrderDto.OrderInfo order = orderFacade.placeOrder(
                        userId,
                        List.of(new OrderDto.OrderLineCommand(limitedProductId, 1)),
                        null
                    );
                    successIds.add(order.id());
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successIds).hasSize(2);
        assertThat(failures).hasSize(3);
        Product product = productRepository.findById(limitedProductId).orElseThrow();
        assertThat(product.getStock()).isEqualTo(0);
    }

    @DisplayName("동일한 쿠폰으로 동시에 주문해도 쿠폰은 단 한 번만 사용된다")
    @Test
    void placeOrder_concurrent_couponUsedOnce() throws InterruptedException {
        CouponDto.CouponInfo template = couponFacade.registerTemplate(
            "동시주문쿠폰", null, CouponType.FIXED,
            BigDecimal.valueOf(1000), ZonedDateTime.now().plusDays(30)
        );
        CouponDto.CouponIssueInfo issued = couponFacade.issue(userId, template.id());
        Long couponIssueId = issued.id();

        int concurrency = 5;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(concurrency);
        List<Long> successIds = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    OrderDto.OrderInfo order = orderFacade.placeOrder(
                        userId,
                        List.of(new OrderDto.OrderLineCommand(productId, 1)),
                        couponIssueId
                    );
                    successIds.add(order.id());
                } catch (Throwable t) {
                    failures.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successIds).hasSize(1);
        assertThat(failures).hasSize(concurrency - 1);
        assertThat(failures).allSatisfy(t ->
            assertThat(t)
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.COUPON_NOT_AVAILABLE))
        );
    }
}
