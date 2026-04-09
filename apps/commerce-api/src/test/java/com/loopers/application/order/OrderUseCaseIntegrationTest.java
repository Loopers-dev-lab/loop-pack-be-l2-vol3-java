package com.loopers.application.order;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.coupon.CouponAdminApplicationService;
import com.loopers.application.coupon.command.CreateCouponCommand;
import com.loopers.application.order.command.CreateOrderCommand;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.process.checkout.OrderUseCase;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.point.PointBalance;
import com.loopers.domain.point.PointBalanceRepository;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class OrderUseCaseIntegrationTest {

    private static final int DEFAULT_INITIAL_POINT_BALANCE = 1_000_000;

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CouponAdminApplicationService couponAdminApplicationService;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private PointBalanceRepository pointBalanceRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGatewayTestConfig.FakePaymentGateway fakePaymentGateway;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void mockGateway() {
        fakePaymentGateway.clear();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("OrderUseCase 통합: 주문 생성 시 즉시 재고를 차감하지 않는다")
    void createOrderWithoutImmediateStockDeduction() {
        UUID categoryId = categoryRepository.save(new Category("주문카테고리")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품",
                5000,
                5,
                "desc",
                categoryId,
                brandId
        ));

        Order order = orderUseCase.create(new CreateOrderCommand(
                "orderusemember",
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 2)),
                null
        ));

        Product updated = productApplicationService.get(product.id());
        assertThat(order.id()).isNotNull();
        assertThat(updated.stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("OrderUseCase 통합: 주문 취소 시 쿠폰이 복구되어 재사용 가능")
    void cancelOrderRestoresCoupon() {
        String memberId = "ordercouponmember";
        UUID categoryId = categoryRepository.save(new Category("주문카테고리2")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드2", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품2",
                5000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                "주문취소복구쿠폰",
                CouponType.FIXED,
                1000,
                0,
                100,
                LocalDateTime.now().plusDays(3)
        ));

        issuedCouponRepository.save(new IssuedCoupon(
                memberId,
                coupon.id(),
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                null
        ));

        Order firstOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        orderUseCase.cancel(new OrderAccessRequest(firstOrder.id(), memberId, false));

        Order secondOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        Product updated = productApplicationService.get(product.id());
        assertThat(secondOrder.id()).isNotNull();
        assertThat(updated.stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("OrderUseCase 통합: 관리자 취소에서도 쿠폰 복구가 동작한다")
    void adminCancelAlsoRestoresCoupon() {
        String memberId = "ordercouponadmin";
        UUID categoryId = categoryRepository.save(new Category("주문카테고리3")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드3", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품3",
                5000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                "관리자취소복구쿠폰",
                CouponType.FIXED,
                1000,
                0,
                100,
                LocalDateTime.now().plusDays(3)
        ));

        issuedCouponRepository.save(new IssuedCoupon(
                memberId,
                coupon.id(),
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                null
        ));

        Order firstOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        orderUseCase.cancel(new OrderAccessRequest(firstOrder.id(), null, true));

        Order secondOrder = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id()
        ));

        Product updated = productApplicationService.get(product.id());
        assertThat(secondOrder.id()).isNotNull();
        assertThat(updated.stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("OrderUseCase 통합: 쿠폰 최소 주문금액은 상품 합계 기준으로 검증된다")
    void createOrderFailsWhenCouponMinOrderAmountNotSatisfiedByItemSum() {
        String memberId = "ordercouponamountmember";
        UUID categoryId = categoryRepository.save(new Category("주문카테고리4")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드4", "desc", "img")).id();

        Product productA = productApplicationService.create(new CreateProductCommand(
                "주문상품4-A",
                2000,
                10,
                "desc",
                categoryId,
                brandId
        ));
        Product productB = productApplicationService.create(new CreateProductCommand(
                "주문상품4-B",
                3000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                "최소주문금액검증쿠폰",
                CouponType.FIXED,
                1000,
                6000,
                100,
                LocalDateTime.now().plusDays(3)
        ));

        issuedCouponRepository.save(new IssuedCoupon(
                memberId,
                coupon.id(),
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                null
        ));

        assertThatThrownBy(() -> orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(
                        new CreateOrderCommand.OrderItemCommand(productA.id(), 1),
                        new CreateOrderCommand.OrderItemCommand(productB.id(), 1)
                ),
                coupon.id()
        ))).isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("OrderUseCase 통합: 결제 FAILED 상태 주문 취소 시 보상은 수행되고 PG 취소는 호출되지 않는다")
    void cancelOrderSkipsPaymentCancelWhenPaymentFailed() {
        String memberId = "orderpayfailedmember";
        UUID categoryId = categoryRepository.save(new Category("주문카테고리5")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드5", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품5",
                7000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                "결제실패복구쿠폰",
                CouponType.FIXED,
                1000,
                0,
                100,
                LocalDateTime.now().plusDays(3)
        ));
        issuedCouponRepository.save(new IssuedCoupon(
                memberId,
                coupon.id(),
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                null
        ));

        int pointAmount = 2000;
        Order order = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id(),
                pointAmount,
                com.loopers.domain.payment.CardType.SAMSUNG,
                "1234-5678-1234-5678"
        ));

        Payment requested = awaitPaymentByOrder(memberId, order.id());
        paymentRepository.save(requested.markFailed("결제 실패"));

        orderUseCase.cancel(new OrderAccessRequest(order.id(), memberId, false));
        Order cancelled = awaitOrderStatus(order.id(), "CANCELLED");

        IssuedCoupon restoredCoupon = issuedCouponRepository.findByMemberIdAndCouponId(memberId, coupon.id())
                .orElseThrow();
        assertThat(restoredCoupon.status()).isEqualTo(CouponStatus.AVAILABLE);

        PointBalance pointBalance = pointBalanceRepository.findByMemberId(memberId).orElseThrow();
        assertThat(pointBalance.balance()).isEqualTo(DEFAULT_INITIAL_POINT_BALANCE);
        assertThat(productApplicationService.get(product.id()).stock()).isEqualTo(10);

        assertCancelNotCalledWithin(500);
    }

    @Test
    @DisplayName("OrderUseCase 통합: 결제 CANCEL_FAILED 상태 주문 취소 시 PG 취소 재시도를 호출한다")
    void cancelOrderTriggersPaymentCancelWhenPaymentCancelFailed() {
        String memberId = "ordercancelfailedmember";
        UUID categoryId = categoryRepository.save(new Category("주문카테고리6")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("주문브랜드6", "desc", "img")).id();
        Product product = productApplicationService.create(new CreateProductCommand(
                "주문상품6",
                9000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Coupon coupon = couponAdminApplicationService.create(new CreateCouponCommand(
                "취소실패복구쿠폰",
                CouponType.FIXED,
                1000,
                0,
                100,
                LocalDateTime.now().plusDays(3)
        ));
        issuedCouponRepository.save(new IssuedCoupon(
                memberId,
                coupon.id(),
                CouponStatus.AVAILABLE,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(3),
                null
        ));

        int pointAmount = 2000;
        Order order = orderUseCase.create(new CreateOrderCommand(
                memberId,
                List.of(new CreateOrderCommand.OrderItemCommand(product.id(), 1)),
                coupon.id(),
                pointAmount,
                com.loopers.domain.payment.CardType.SAMSUNG,
                "1234-5678-1234-5678"
        ));

        Payment requested = awaitPaymentByOrder(memberId, order.id());
        Payment succeeded = requested.markSucceeded(requested.pgTransactionKey());
        Payment cancelRequested = succeeded.requestCancel();
        paymentRepository.save(cancelRequested.markCancelFailed("PG 취소 실패"));

        orderUseCase.cancel(new OrderAccessRequest(order.id(), memberId, false));
        Order cancelled = awaitOrderStatus(order.id(), "CANCELLED");

        IssuedCoupon restoredCoupon = issuedCouponRepository.findByMemberIdAndCouponId(memberId, coupon.id())
                .orElseThrow();
        assertThat(restoredCoupon.status()).isEqualTo(CouponStatus.AVAILABLE);

        PointBalance pointBalance = pointBalanceRepository.findByMemberId(memberId).orElseThrow();
        assertThat(pointBalance.balance()).isEqualTo(DEFAULT_INITIAL_POINT_BALANCE);
        assertThat(productApplicationService.get(product.id()).stock()).isEqualTo(10);

        awaitCancelCallCountAtLeast(1);
    }

    private Payment awaitPaymentByOrder(String memberId, UUID orderId) {
        for (int i = 0; i < 40; i++) {
            var found = paymentRepository.findByMemberIdAndOrderId(memberId, orderId);
            if (found.isPresent()) {
                return found.get();
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("결제 생성 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
        throw new AssertionError("결제 데이터가 생성되지 않았습니다.");
    }

    private Order awaitOrderStatus(UUID orderId, String status) {
        for (int i = 0; i < 40; i++) {
            Order found = orderApplicationService.getById(new OrderAccessRequest(orderId, null, true));
            if (found.status().name().equals(status)) {
                return found;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("주문 상태 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
        throw new AssertionError("주문 상태가 기대치에 도달하지 못했습니다. expected=" + status);
    }

    private void assertCancelNotCalledWithin(long millis) {
        int attempts = (int) (millis / 50L);
        for (int i = 0; i < attempts; i++) {
            if (fakePaymentGateway.cancelCallCount() > 0) {
                throw new AssertionError("PG 취소 호출이 발생하면 안 됩니다. actual=" + fakePaymentGateway.cancelCallCount());
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("취소 미호출 검증 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
    }

    private void awaitCancelCallCountAtLeast(int expected) {
        for (int i = 0; i < 40; i++) {
            if (fakePaymentGateway.cancelCallCount() >= expected) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("PG 취소 호출 대기 중 인터럽트가 발생했습니다.", e);
            }
        }
        throw new AssertionError("PG 취소 호출이 기대치에 도달하지 못했습니다. expected=" + expected
                + ", actual=" + fakePaymentGateway.cancelCallCount());
    }

    @TestConfiguration
    static class PaymentGatewayTestConfig {

        @Bean
        @Primary
        FakePaymentGateway fakePaymentGateway() {
            return new FakePaymentGateway();
        }

        static class FakePaymentGateway implements PaymentGateway {

            private final AtomicInteger sequence = new AtomicInteger();
            private final AtomicInteger cancelCallCount = new AtomicInteger();
            private final Map<String, PaymentGatewayTransaction> byTransactionKey = new ConcurrentHashMap<>();

            @Override
            public PaymentGatewayTransaction requestPayment(PaymentGatewayRequest request) {
                String transactionKey = "TEST-TRX-" + sequence.incrementAndGet();
                PaymentGatewayTransaction transaction = new PaymentGatewayTransaction(
                        transactionKey,
                        request.orderReference(),
                        PaymentStatus.REQUESTED,
                        null
                );
                byTransactionKey.put(transactionKey, transaction);
                return transaction;
            }

            @Override
            public PaymentGatewayTransaction getPayment(String memberId, String transactionKey) {
                return byTransactionKey.getOrDefault(
                        transactionKey,
                        new PaymentGatewayTransaction(transactionKey, "ORDER-REF", PaymentStatus.REQUESTED, null)
                );
            }

            @Override
            public List<PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference) {
                return byTransactionKey.values().stream()
                        .filter(transaction -> orderReference.equals(transaction.orderReference()))
                        .toList();
            }

            @Override
            public PaymentGatewayTransaction cancelPayment(PaymentGatewayCancelRequest request) {
                cancelCallCount.incrementAndGet();
                PaymentGatewayTransaction found = byTransactionKey.get(request.transactionKey());
                PaymentGatewayTransaction cancelled = new PaymentGatewayTransaction(
                        request.transactionKey(),
                        found == null ? "ORDER-REF" : found.orderReference(),
                        PaymentStatus.CANCEL_REQUESTED,
                        null
                );
                byTransactionKey.put(request.transactionKey(), cancelled);
                return cancelled;
            }

            int cancelCallCount() {
                return cancelCallCount.get();
            }

            void clear() {
                sequence.set(0);
                cancelCallCount.set(0);
                byTransactionKey.clear();
            }
        }
    }
}
