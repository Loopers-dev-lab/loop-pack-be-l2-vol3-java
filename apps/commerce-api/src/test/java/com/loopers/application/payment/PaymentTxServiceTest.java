package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentTxServiceTest {

    private PaymentRepository paymentRepository;
    private OrderRepository orderRepository;
    private ProductRepository productRepository;
    private CouponIssueRepository couponIssueRepository;
    private PaymentTxService paymentTxService;

    private final Long orderId = 100L;
    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        couponIssueRepository = mock(CouponIssueRepository.class);
        paymentTxService = new PaymentTxService(paymentRepository, orderRepository, productRepository, couponIssueRepository);
    }

    // ── refundPayment ────────────────────────────────────────────────────

    @DisplayName("refundPayment: SUCCESS 결제가 있으면 REFUNDED로 전환한다")
    @Test
    void refundPayment_success_toRefunded() {
        Payment payment = Payment.create(orderId, userId, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        payment.markSuccess("txId1");
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        paymentTxService.refundPayment(orderId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepository).save(payment);
    }

    @DisplayName("refundPayment: PENDING 결제가 있으면 FAILED로 전환한다")
    @Test
    void refundPayment_pending_toFailed() {
        Payment payment = Payment.create(orderId, userId, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        paymentTxService.refundPayment(orderId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @DisplayName("refundPayment: FAILED 결제가 있으면 아무것도 하지 않는다")
    @Test
    void refundPayment_failed_noChange() {
        Payment payment = Payment.create(orderId, userId, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        payment.markFailed(null);
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        paymentTxService.refundPayment(orderId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepository).save(payment);
    }

    @DisplayName("refundPayment: 결제가 없으면 아무것도 하지 않는다")
    @Test
    void refundPayment_noPayment_noop() {
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        paymentTxService.refundPayment(orderId);

        verify(paymentRepository, never()).save(any());
    }

    // ── processPaymentResult: 콜백-취소 경쟁 조건 ──────────────────────────

    @DisplayName("processPaymentResult: SUCCESS 콜백이 왔지만 주문이 이미 CANCELLED면 REFUNDED로 처리한다")
    @Test
    void processPaymentResult_success_butOrderCancelled_refunded() {
        Payment payment = Payment.create(orderId, userId, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Order order = createCancelledOrder(userId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        paymentTxService.processPaymentResult(1L, orderId, "txId1", "SUCCESS");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(orderRepository, never()).save(any());
        verify(paymentRepository).save(payment);
    }

    @DisplayName("processPaymentResult: SUCCESS 콜백 + 정상 주문이면 CONFIRMED로 처리한다")
    @Test
    void processPaymentResult_success_orderConfirmed() {
        Payment payment = Payment.create(orderId, userId, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Order order = createPendingOrder(userId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        paymentTxService.processPaymentResult(1L, orderId, "txId1", "SUCCESS");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
    }

    @DisplayName("processPaymentResult: FAILED 콜백이면 주문 취소 및 재고 복구한다")
    @Test
    void processPaymentResult_failed_orderCancelledWithRecovery() {
        Payment payment = Payment.create(orderId, userId, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        Order order = createPendingOrder(userId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Product product = mock(Product.class);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        paymentTxService.processPaymentResult(1L, orderId, "txId1", "FAILED");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(product).increaseStock(1);
    }

    @DisplayName("processPaymentResult: 이미 처리된 결제(non-PENDING)는 무시한다")
    @Test
    void processPaymentResult_idempotent_whenAlreadyProcessed() {
        Payment payment = Payment.create(orderId, userId, "SAMSUNG", "1234-5678", BigDecimal.valueOf(5000));
        payment.markFailed(null);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        paymentTxService.processPaymentResult(1L, orderId, "txId1", "SUCCESS");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderRepository, never()).findById(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Order createPendingOrder(Long userId) {
        OrderItem item = OrderItem.createSnapshot(10L, "테스트상품", BigDecimal.valueOf(5000), 1);
        return Order.create(userId, List.of(item));
    }

    private Order createCancelledOrder(Long userId) {
        Order order = createPendingOrder(userId);
        order.cancel();
        return order;
    }
}
