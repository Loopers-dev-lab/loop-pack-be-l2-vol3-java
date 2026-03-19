package com.loopers.application.payment;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentFixture;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductService;

@ExtendWith(MockitoExtension.class)
class PaymentProcessorTest {

    @InjectMocks
    private PaymentProcessor paymentProcessor;

    @Mock
    private OrderService orderService;

    @Mock
    private OwnedCouponService ownedCouponService;

    @Mock
    private ProductService productService;

    @Mock
    private PaymentService paymentService;

    @DisplayName("결제 성공 후속 처리를 수행할 때,")
    @Nested
    class HandleSuccess {

        @DisplayName("쿠폰이 적용된 주문이면, 주문 완료 후 쿠폰을 사용 처리한다.")
        @Test
        void paysOrderAndUsesCoupon_whenCouponApplied() {
            // arrange
            Order order = mock(Order.class);
            given(orderService.pay(1L)).willReturn(order);
            given(order.hasAppliedCoupon()).willReturn(true);
            given(order.getOwnedCouponId()).willReturn(10L);

            // act
            paymentProcessor.handleSuccess(1L);

            // assert
            then(orderService).should().pay(1L);
            then(ownedCouponService).should().use(10L);
        }

        @DisplayName("쿠폰이 적용되지 않은 주문이면, 주문 완료만 수행한다.")
        @Test
        void paysOrderOnly_whenNoCouponApplied() {
            // arrange
            Order order = mock(Order.class);
            given(orderService.pay(1L)).willReturn(order);
            given(order.hasAppliedCoupon()).willReturn(false);

            // act
            paymentProcessor.handleSuccess(1L);

            // assert
            then(orderService).should().pay(1L);
            then(ownedCouponService).should(never()).use(1L);
        }
    }

    @DisplayName("결제 실패 후속 처리를 수행할 때,")
    @Nested
    class HandleFailure {

        @DisplayName("주문을 실패 처리하고 재고를 복원한다.")
        @Test
        void failsOrderAndRestoresStock() {
            // arrange
            OrderItem item1 = mock(OrderItem.class);
            given(item1.getProductId()).willReturn(1L);
            given(item1.getQuantity()).willReturn(3L);

            OrderItem item2 = mock(OrderItem.class);
            given(item2.getProductId()).willReturn(2L);
            given(item2.getQuantity()).willReturn(5L);

            Order order = mock(Order.class);
            given(orderService.fail(1L)).willReturn(order);
            given(order.getOrderItems()).willReturn(List.of(item1, item2));

            // act
            paymentProcessor.handleFailure(1L);

            // assert
            then(orderService).should().fail(1L);
            then(productService).should().restoreStock(1L, 3L);
            then(productService).should().restoreStock(2L, 5L);
        }
    }

    @DisplayName("PG 거래가 존재하는 READY 결제를 복구할 때,")
    @Nested
    class RecoverWithTransaction {

        @DisplayName("SUCCESS이면, 결제를 확정하고 성공 후속 처리를 수행한다.")
        @Test
        void confirmsAndHandlesSuccess_whenStatusIsSuccess() {
            // arrange
            Payment payment = PaymentFixture.createReadyPayment();
            given(paymentService.confirmPayment(payment.getId(), "txn-recovered"))
                    .willReturn(payment);

            Order order = mock(Order.class);
            given(orderService.pay(payment.getOrderId())).willReturn(order);
            given(order.hasAppliedCoupon()).willReturn(false);

            // act
            paymentProcessor.recoverWithTransaction(
                    payment.getId(), "txn-recovered", PaymentStatus.SUCCESS, null
            );

            // assert
            then(paymentService).should().confirmPayment(payment.getId(), "txn-recovered");
            then(orderService).should().pay(payment.getOrderId());
        }

        @DisplayName("FAILED이면, 결제를 확정하고 실패 후속 처리를 수행한다.")
        @Test
        void confirmsAndHandlesFailure_whenStatusIsFailed() {
            // arrange
            Payment payment = PaymentFixture.createReadyPayment();
            given(paymentService.confirmPayment(payment.getId(), "txn-failed"))
                    .willReturn(payment);

            Order order = mock(Order.class);
            given(orderService.fail(payment.getOrderId())).willReturn(order);
            given(order.getOrderItems()).willReturn(List.of());

            // act
            paymentProcessor.recoverWithTransaction(
                    payment.getId(), "txn-failed", PaymentStatus.FAILED, "잔액 부족"
            );

            // assert
            then(paymentService).should().confirmPayment(payment.getId(), "txn-failed");
            then(orderService).should().fail(payment.getOrderId());
        }
    }

    @DisplayName("PG 거래가 없는 READY 결제를 복구할 때,")
    @Nested
    class RecoverWithoutTransaction {

        @DisplayName("결제를 실패 처리하고 실패 후속 처리를 수행한다.")
        @Test
        void failsPaymentAndHandlesFailure() {
            // arrange
            Payment payment = PaymentFixture.createReadyPayment();
            given(paymentService.fail(payment.getId(), "PG 결제 요청 타임아웃으로 거래 없음"))
                    .willReturn(payment);

            Order order = mock(Order.class);
            given(orderService.fail(payment.getOrderId())).willReturn(order);
            given(order.getOrderItems()).willReturn(List.of());

            // act
            paymentProcessor.recoverWithoutTransaction(
                    payment.getId(), "PG 결제 요청 타임아웃으로 거래 없음"
            );

            // assert
            then(paymentService).should().fail(payment.getId(), "PG 결제 요청 타임아웃으로 거래 없음");
            then(paymentService).should(never()).confirmPayment(payment.getId(), null);
            then(orderService).should().fail(payment.getOrderId());
        }
    }
}
