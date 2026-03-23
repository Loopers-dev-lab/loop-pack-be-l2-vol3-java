package com.loopers.application.payment;

import com.loopers.domain.coupon.CouponIssueRepository;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentTxService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CouponIssueRepository couponIssueRepository;

    @Transactional
    public Payment createPayment(Long orderId, Long userId, String cardType, String cardNo, BigDecimal amount) {
        Payment payment = Payment.create(orderId, userId, cardType, cardNo, amount);
        return paymentRepository.save(payment);
    }

    @Transactional
    public void assignTransactionId(Long paymentId, String pgTransactionId) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.assignTransactionId(pgTransactionId);
            paymentRepository.save(payment);
        });
    }

    /**
     * PG 결과를 내부 시스템에 반영한다.
     * SUCCESS: Payment → SUCCESS, Order → CONFIRMED
     * FAILED:  Payment → FAILED, Order → CANCELLED (재고/쿠폰 복구)
     * 이미 처리된 결제(non-PENDING)는 멱등하게 무시한다.
     */
    @Transactional
    public void processPaymentResult(Long paymentId, Long orderId, String pgTransactionId, String pgStatus) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new CoreException(ErrorType.PAYMENT_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));

        if ("SUCCESS".equals(pgStatus)) {
            payment.markSuccess(pgTransactionId);
            if (order.getStatus() == OrderStatus.CANCELLED) {
                // 주문이 이미 취소된 상태에서 SUCCESS 콜백 도착 → 환불 처리
                payment.markRefunded();
            } else {
                order.changeStatus(OrderStatus.CONFIRMED);
                orderRepository.save(order);
            }
        } else {
            payment.markFailed(pgTransactionId);
            cancelOrderWithRecovery(order);
        }

        paymentRepository.save(payment);
    }

    /**
     * 주문 취소로 인한 결제 처리.
     * - SUCCESS → REFUNDED (결제 완료 후 주문 취소)
     * - PENDING → FAILED  (결제 진행 중 주문 취소 — PG 결과 무관하게 무효화)
     */
    @Transactional
    public void refundPayment(Long orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                payment.markRefunded();
            } else if (payment.getStatus() == PaymentStatus.PENDING) {
                payment.markFailed(null);
            }
            paymentRepository.save(payment);
        });
    }

    private void cancelOrderWithRecovery(Order order) {
        if (!order.cancel()) {
            return;
        }

        for (OrderItem item : order.getOrderItems()) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.increaseStock(item.getQuantity());
                productRepository.save(product);
            });
        }

        if (order.getCouponId() != null) {
            couponIssueRepository.findByIdAndUserId(order.getCouponId(), order.getUserId())
                .ifPresent(issue -> {
                    issue.revoke();
                    couponIssueRepository.save(issue);
                });
        }

        orderRepository.save(order);
    }
}
