package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentApp {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentInfo pay(String orderId, String cardType, String cardNo, Long userId) {
        Order order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "찾을 수 없는 주문번호입니다."));

        if (!order.userId().equals(userId)) throw new CoreException(ErrorType.FORBIDDEN, "본인의 주문에 대해서만 결제 가능합니다.");

        Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();
            if (payment.status() == PaymentStatus.SUCCESS) {
                throw new CoreException(ErrorType.CONFLICT, "이미 결제된 주문입니다.");
            }
            if (payment.status() == PaymentStatus.PENDING) {
                throw new CoreException(ErrorType.CONFLICT, "이미 진행 중인 결제입니다.");
            }
            payment.reset(cardType, cardNo);
            return PaymentInfo.from(payment);
        }

        Payment payment = Payment.of(orderId, userId, cardType, cardNo, order.paymentAmount());
        paymentRepository.save(payment);
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(String orderId, Long userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "찾을 수 없는 결제번호입니다."));

        if (!payment.userId().equals(userId)) throw new CoreException(ErrorType.NOT_FOUND, "찾을 수 없는 결제번호입니다.");

        return PaymentInfo.from(payment);
    }

    @Transactional
    public void applyPgResponse(String orderId, String transactionKey, String status, String reason) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "찾을 수 없는 결제번호입니다."));

        payment.applyPgResult(transactionKey, PaymentStatus.from(status), reason);
    }
}
