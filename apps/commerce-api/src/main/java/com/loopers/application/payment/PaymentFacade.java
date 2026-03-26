package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.ExternalPaymentClient;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.users.UserService;
import com.loopers.domain.users.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ExternalPaymentClient externalPaymentClient;

    @Value("${payment.callback-url}")
    private String callbackUrl;

    public PaymentInfo pay(String loginId, String password, Long orderId, CardType cardType, String cardNo) {
        Users user = userService.authenticate(loginId, password);
        Order order = orderService.getOrder(user.getId(), orderId);

        // TX1: PENDING 상태로 먼저 저장 (커밋)
        Payment payment = paymentService.createPending(order.getId(), user.getId(), cardType, cardNo, order.getFinalPrice());

        // TX 밖에서 PG 호출 — 실패해도 PENDING 상태로 응답
        try {
            externalPaymentClient.requestPayment(order.getId(), cardType, cardNo, order.getFinalPrice(), callbackUrl);
        } catch (Exception e) {
            log.warn("PG 결제 요청 실패. orderId={}, 이유={}", orderId, e.getMessage());
        }

        return PaymentInfo.from(payment);
    }

    public void handleCallback(Long orderId, String transactionId, boolean success) {
        paymentService.handleCallback(orderId, transactionId, success);
        if (success) {
            orderService.confirmOrder(orderId);
        }
    }

    public void syncPayment(Long orderId) {
        var response = externalPaymentClient.getPaymentByOrderId(orderId);
        paymentService.handleCallback(orderId, response.transactionId(), response.success());
        if (response.success()) {
            orderService.confirmOrder(orderId);
        }
    }
}