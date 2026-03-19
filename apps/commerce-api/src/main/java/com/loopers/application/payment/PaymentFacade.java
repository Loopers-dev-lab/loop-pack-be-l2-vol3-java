package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.gateway.PaymentQueryResult;
import com.loopers.domain.payment.gateway.PgType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentGatewayExecutor gatewayExecutor;
    private final PaymentBusinessProcessor businessProcessor;
    private final TransactionTemplate transactionTemplate;

    // Command

    @Bulkhead(name = "pg-payment", fallbackMethod = "paymentBulkheadFallback")
    public PaymentInfo requestPayment(Long userId, PaymentCommand.Request command) {
        // TX1: 주문 검증 + Payment 생성 + 비즈니스 확정
        Payment payment = transactionTemplate.execute(status -> {
            Order order = validateAndGetOrder(userId, command.orderId());

            PaymentCommand.Create createCommand = PaymentCommand.Create.of(
                    command.orderId(), userId, command.pgType(),
                    command.cardType(), command.cardNo(), order.getFinalAmount());
            Payment created = paymentService.createPayment(createCommand);

            businessProcessor.confirm(order);

            return created;
        });

        // PG 호출 (트랜잭션 밖)
        PgConfirmOutcome outcome = gatewayExecutor.confirm(payment);
        handleConfirmOutcome(payment, outcome);

        return PaymentInfo.from(paymentService.getPayment(payment.getId()));
    }

    @Transactional
    public void handleCallback(String paymentKey, String pgStatus, String reason) {
        Payment payment = paymentService.getPaymentByPaymentKey(paymentKey)
                .orElse(null);
        if (payment == null || payment.isFinalized()) {
            return;
        }

        if ("SUCCESS".equals(pgStatus)) {
            payment.markSucceeded();
        } else {
            payment.markFailed(reason);
            businessProcessor.compensate(orderService.getOrder(payment.getOrderId()));
        }
    }

    public void cancelPayment(Long userId, Long orderId) {
        Payment payment = paymentService.getLatestPaymentByOrderId(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다"));

        doCancelPayment(userId, payment, "주문 취소");
    }

    public PaymentInfo cancelPaymentById(Long userId, Long paymentId, PaymentCommand.Cancel command) {
        Payment payment = paymentService.getPayment(paymentId);

        doCancelPayment(userId, payment, command.cancelReason());

        return PaymentInfo.from(paymentService.getPayment(paymentId));
    }

    @Bulkhead(name = "pg-payment", fallbackMethod = "verifyBulkheadFallback")
    public PaymentInfo verifyPayment(Long userId, Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        payment.validateOwnership(userId);
        if (payment.isFinalized()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 확정된 결제입니다");
        }

        if (payment.getStatus() == PaymentStatus.REQUESTED) {
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.markFailed(payment.getId(), "결제 미완료");
                businessProcessor.compensate(orderService.getOrder(payment.getOrderId()));
            });
            return PaymentInfo.from(paymentService.getPayment(payment.getId()));
        }

        // IN_PROGRESS: PG에서 상태 조회
        PaymentQueryResult result = gatewayExecutor.query(payment);

        if (result.found() && result.done()) {
            paymentService.markSucceeded(payment.getId());
        } else if (result.found()) {
            // PG에서 아직 처리 중 → 상태 변경 없음
        } else {
            // PG에 결제 정보 없음 또는 실패
            transactionTemplate.executeWithoutResult(status -> {
                paymentService.markFailed(payment.getId(), "결제 미완료");
                businessProcessor.compensate(orderService.getOrder(payment.getOrderId()));
            });
        }

        return PaymentInfo.from(paymentService.getPayment(payment.getId()));
    }

    // Query

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentDetail(Long userId, Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        payment.validateOwnership(userId);
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        order.validateOwnership(userId);
        return paymentService.getLatestPaymentByOrderId(orderId)
                .map(PaymentInfo::from)
                .orElse(PaymentInfo.empty(orderId));
    }

    public List<PgType> getAvailableMethods() {
        return gatewayRegistry.getAvailableTypes();
    }

    private void doCancelPayment(Long userId, Payment payment, String cancelReason) {
        payment.validateOwnership(userId);
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 결제 상태입니다");
        }

        // PG 취소 (트랜잭션 밖)
        gatewayExecutor.cancel(payment, cancelReason);

        // TX: 결제 취소 + 보상
        transactionTemplate.executeWithoutResult(status -> {
            paymentService.markCanceled(payment.getId(), cancelReason);
            businessProcessor.compensate(orderService.getOrder(payment.getOrderId()));
        });
    }

    private void handleConfirmOutcome(Payment payment, PgConfirmOutcome outcome) {
        switch (outcome) {
            case PgConfirmOutcome.Success() -> transactionTemplate.executeWithoutResult(status -> {
                Payment p = paymentService.getPayment(payment.getId());
                if (!p.isFinalized()) p.markSucceeded();
            });
            case PgConfirmOutcome.Failed(String reason) -> {
                transactionTemplate.executeWithoutResult(status -> {
                    paymentService.markFailed(payment.getId(), reason);
                    businessProcessor.compensate(orderService.getOrder(payment.getOrderId()));
                });
                throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요");
            }
            case PgConfirmOutcome.Timeout() -> {
                // REQUESTED 상태 유지, 콜백/verify로 최종 결정
            }
        }
    }

    private Order validateAndGetOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        order.validateOwnership(userId);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제할 수 없는 주문 상태입니다");
        }
        if (paymentService.existsActivePayment(orderId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중이거나 완료된 주문입니다");
        }
        return order;
    }

    private PaymentInfo paymentBulkheadFallback(Long userId, PaymentCommand.Request command, Throwable t) {
        if (t instanceof CoreException) {
            throw (CoreException) t;
        }
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청이 많습니다. 잠시 후 다시 시도해주세요");
    }

    private PaymentInfo verifyBulkheadFallback(Long userId, Long paymentId, Throwable t) {
        if (t instanceof CoreException) {
            throw (CoreException) t;
        }
        throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 확인 요청이 많습니다. 잠시 후 다시 시도해주세요");
    }
}
