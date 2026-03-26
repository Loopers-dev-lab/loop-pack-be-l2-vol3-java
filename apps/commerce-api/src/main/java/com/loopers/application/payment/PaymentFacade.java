package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFacade {

    private final UserService userService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PgPaymentGateway pgPaymentGateway;

    public PaymentInfo pay(String loginId, String password, Long orderId, CardType cardType, String cardNo) {
        UserModel user = userService.getMyInfo(loginId, password);
        OrderModel order = orderService.getMyOrder(user.getId(), orderId);

        PaymentModel payment = paymentService.createOrGet(user.getId(), orderId, order.getTotalAmount(), cardType, cardNo);
        if (payment.isTerminal()) {
            OrderModel currentOrder = orderService.getMyOrder(user.getId(), orderId);
            return PaymentInfo.from(payment, currentOrder.getStatus());
        }

        try {
            PgPaymentRequestResult result = pgPaymentGateway.requestPayment(
                new PgPaymentRequest(orderId, cardType, cardNo, order.getTotalAmount())
            );
            payment = paymentService.applyExternalResult(payment.getId(), result.paymentKey(), PaymentStatus.PENDING, null);
        } catch (Exception e) {
            log.warn("PG 결제 요청 실패. orderId={}", orderId, e);
            Optional<PgPaymentSnapshot> recovered = pgPaymentGateway.getPaymentByOrderId(orderId);
            if (recovered.isPresent()) {
                payment = applySnapshot(payment.getId(), recovered.get());
            } else {
                payment = paymentService.markPendingWithError(payment.getId(), "PG 요청 실패: " + e.getClass().getSimpleName());
            }
        }

        OrderModel currentOrder = orderService.getMyOrder(user.getId(), orderId);
        return PaymentInfo.from(payment, currentOrder.getStatus());
    }

    public PaymentInfo getMyPayment(String loginId, String password, Long orderId) {
        UserModel user = userService.getMyInfo(loginId, password);
        PaymentModel payment = paymentService.getMyPayment(user.getId(), orderId);
        OrderModel order = orderService.getMyOrder(user.getId(), orderId);
        return PaymentInfo.from(payment, order.getStatus());
    }

    public PaymentInfo syncMyPayment(String loginId, String password, Long orderId) {
        UserModel user = userService.getMyInfo(loginId, password);
        PaymentModel payment = paymentService.getMyPayment(user.getId(), orderId);

        Optional<PgPaymentSnapshot> snapshot = payment.getPgPaymentKey() != null
            ? pgPaymentGateway.getPaymentByKey(payment.getPgPaymentKey())
            : pgPaymentGateway.getPaymentByOrderId(orderId);

        if (snapshot.isPresent()) {
            payment = applySnapshot(payment.getId(), snapshot.get());
        } else {
            payment = paymentService.markPendingWithError(payment.getId(), "PG 조회 결과가 없습니다.");
        }

        OrderModel order = orderService.getMyOrder(user.getId(), orderId);
        return PaymentInfo.from(payment, order.getStatus());
    }

    public void handleCallback(Long orderId, String paymentKey, PgPaymentStatus status, String reason) {
        Optional<PaymentModel> payment = findByOrderIdOrPaymentKey(orderId, paymentKey);
        if (payment.isEmpty()) {
            log.warn("결제 콜백 대상 없음. orderId={}, paymentKey={}", orderId, paymentKey);
            return;
        }

        applySnapshot(payment.get().getId(), new PgPaymentSnapshot(orderId, paymentKey, status, reason));
    }

    public int recoverPendingPayments() {
        List<PaymentModel> targets = paymentService.findRecoverTargets(50, 2);

        int recovered = 0;
        for (PaymentModel payment : targets) {
            Optional<PgPaymentSnapshot> snapshot = payment.getPgPaymentKey() != null
                ? pgPaymentGateway.getPaymentByKey(payment.getPgPaymentKey())
                : pgPaymentGateway.getPaymentByOrderId(payment.getOrderId());

            if (snapshot.isPresent()) {
                applySnapshot(payment.getId(), snapshot.get());
                recovered += 1;
            }
        }

        return recovered;
    }

    private PaymentModel applySnapshot(Long paymentId, PgPaymentSnapshot snapshot) {
        PaymentStatus mappedStatus = switch (snapshot.status()) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case LIMIT_EXCEEDED -> PaymentStatus.FAILED_LIMIT_EXCEEDED;
            case INVALID_CARD -> PaymentStatus.FAILED_INVALID_CARD;
            case FAILED -> PaymentStatus.FAILED;
            case REQUESTED, PROCESSING, UNKNOWN -> PaymentStatus.PENDING;
        };

        return paymentService.applyExternalResult(paymentId, snapshot.paymentKey(), mappedStatus, snapshot.reason());
    }

    private Optional<PaymentModel> findByOrderIdOrPaymentKey(Long orderId, String paymentKey) {
        if (orderId != null) {
            try {
                return Optional.of(paymentService.getByOrderId(orderId));
            } catch (Exception ignored) {
                // continue to key lookup
            }
        }

        if (paymentKey != null && !paymentKey.isBlank()) {
            try {
                return Optional.of(paymentService.findByPaymentKey(paymentKey));
            } catch (Exception ignored) {
                // no-op
            }
        }
        return Optional.empty();
    }
}
