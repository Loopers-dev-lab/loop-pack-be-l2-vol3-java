package com.loopers.application.payment;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.PaymentErrorCode;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgCallbackStatus;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgRequestStatus;
import com.loopers.support.error.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFacade {

    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;
    private final OrderService orderService;

    public PaymentResult requestPayment(Long userId, PaymentCriteria.Create criteria) {
        PaymentModel payment = paymentService.createPending(
                criteria.orderId(), criteria.amount(),
                criteria.cardType(), maskCardNo(criteria.cardNo()));

        PgPaymentResult pgResult = paymentGateway.requestPayment(
                new PgPaymentRequest(
                        String.format("%06d", criteria.orderId()),
                        criteria.cardType().name(),
                        criteria.cardNo(),
                        criteria.amount(),
                        CALLBACK_URL,
                        String.valueOf(userId)));

        if (pgResult.requested()) {
            paymentService.updateRequested(payment.getId(), pgResult.transactionKey());
            return PaymentResult.from(payment);
        }

        paymentService.failById(payment.getId());
        if (pgResult.status() == PgRequestStatus.VALIDATION_ERROR) {
            throw new CoreException(PaymentErrorCode.PG_REQUEST_FAILED, pgResult.pgDetail());
        }
        throw new CoreException(PaymentErrorCode.PG_SERVICE_UNAVAILABLE);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResult getPaymentStatus(Long orderId) {
        PaymentModel payment = paymentService.findByOrderId(orderId)
                .orElseThrow(() -> new CoreException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        return PaymentStatusResult.from(payment);
    }

    public void handleCallback(String transactionKey, String pgStatus, String pgReason) {
        PgCallbackStatus callbackStatus = PgCallbackStatus.from(pgStatus, pgReason);

        PaymentModel payment;
        if (callbackStatus.isSuccess()) {
            payment = paymentService.updateCompleted(transactionKey);
        } else {
            payment = paymentService.updateFailed(
                    transactionKey, callbackStatus.name(), pgReason);
        }

        if (payment.isCompleted()) {
            try {
                orderService.completeOrder(payment.getOrderId());
            } catch (Exception e) {
                OrderModel order = orderService.getById(payment.getOrderId());
                if (order.isCancelled()) {
                    // TODO: PG 환불 API 연동 시 여기서 refund 호출
                    log.warn("늦은 콜백으로 인한 자동 환불 필요. orderId={}", payment.getOrderId());
                } else {
                    log.warn("Order 상태 갱신 실패. 폴링 배치가 복구 예정. orderId={}",
                            payment.getOrderId(), e);
                }
            }
        }

        if (payment.isFailed()) {
            // TODO: 결제 실패 시 보상 트랜잭션 전략 별도 논의
            log.info("결제 실패. orderId={}, failureCode={}",
                    payment.getOrderId(), payment.getFailureCode());
        }
    }

    private String maskCardNo(String cardNo) {
        if (cardNo == null || cardNo.length() < 4) {
            return cardNo;
        }
        return "****-****-****-" + cardNo.substring(cardNo.length() - 4);
    }
}
