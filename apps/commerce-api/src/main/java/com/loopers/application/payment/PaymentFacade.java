package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.config.PgProperties;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.PgPaymentGateway;
import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TX 분리 패턴으로 PG 호출 동안 DB 커넥션을 점유하지 않음.
 * 클래스 레벨 @Transactional 없음 — 각 단계별로 Service 레벨 TX 사용.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PgPaymentGateway pgPaymentGateway;
    private final PgProperties pgProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * TX 분리 패턴:
     * TX-1 (preparePayment): 주문 검증 + 상태 변경 + 결제 레코드 생성 → DB 커넥션 반환
     * NO TX (PG 호출): 외부 HTTP 통신 — DB 커넥션 미점유
     * TX-2 (assignTransactionKey): transactionKey 저장 → DB 커넥션 반환
     */
    @CircuitBreaker(name = "pg", fallbackMethod = "requestPaymentFallback")
    public PaymentInfo requestPayment(Long userId, PaymentCommand command) {
        // TX-1: DB 작업만 (~15ms), 커밋 후 커넥션 즉시 반환
        PaymentModel payment = paymentService.preparePayment(userId, command);

        // NO TX: PG 호출 (100~500ms, 타임아웃 시 3초) — DB 커넥션 안 잡음
        PgPaymentRequest pgRequest = new PgPaymentRequest(
            String.valueOf(command.orderId()),
            command.cardType().name(),
            command.cardNo(),
            String.valueOf(payment.amount().value()),
            pgProperties.callbackUrl()
        );
        PgPaymentResponse pgResponse = pgPaymentGateway.requestPayment(pgRequest, String.valueOf(userId));

        // TX-2: transactionKey 저장 (~5ms), 커밋 후 커넥션 즉시 반환
        paymentService.assignTransactionKey(payment.getId(), pgResponse.transactionKey());

        return PaymentInfo.from(payment, pgResponse.transactionKey());
    }

    private PaymentInfo requestPaymentFallback(Long userId, PaymentCommand command, Throwable t) {
        log.warn("PG 결제 요청 실패 - userId: {}, orderId: {}, reason: {}", userId, command.orderId(), t.getMessage());
        // TX-1이 이미 커밋됨 → 주문은 PAYMENT_PENDING, 결제는 PENDING 상태로 DB에 존재
        // → Polling 스케줄러가 PENDING 결제를 주기적으로 확인하여 복구
        return PaymentInfo.pgFailed(command.orderId(), "결제 시스템이 불안정합니다. 잠시 후 다시 시도해주세요.");
    }

    @Transactional
    public void handleCallback(String transactionKey, String status, String failureReason) {
        PaymentModel payment = paymentService.getByTransactionKey(transactionKey);
        OrderModel order = orderService.getOrderForAdmin(payment.orderId());

        if ("SUCCESS".equals(status)) {
            payment.markSuccess();
            order.confirmPayment();
        } else if ("FAILED".equals(status)) {
            payment.markFailed(failureReason);
            order.failPayment();
        }

        eventPublisher.publishEvent(new PaymentCompletedEvent(
            payment.getId(), payment.orderId(), payment.userId(), "SUCCESS".equals(status)
        ));
    }

    @Transactional
    public PaymentInfo syncPaymentStatus(Long paymentId) {
        PaymentModel payment = paymentService.getById(paymentId);
        if (payment.status() != PaymentStatus.PENDING) {
            return PaymentInfo.from(payment);
        }

        // Phase A: transactionKey가 있으면 PG에 직접 조회
        if (payment.transactionKey() != null) {
            return syncWithTransactionKey(payment);
        }

        // Phase C: transactionKey 없는 고아 → orderId로 PG 조회
        return syncOrphanPayment(payment);
    }

    private PaymentInfo syncWithTransactionKey(PaymentModel payment) {
        try {
            PgPaymentResponse pgResponse = pgPaymentGateway.getPaymentStatus(
                payment.transactionKey(), String.valueOf(payment.userId())
            );
            applyPgResult(payment, pgResponse.status(), pgResponse.failureReason());
        } catch (Exception e) {
            log.warn("PG 상태 조회 실패 - transactionKey: {}, reason: {}", payment.transactionKey(), e.getMessage());
        }
        return PaymentInfo.from(payment);
    }

    private PaymentInfo syncOrphanPayment(PaymentModel payment) {
        try {
            PgPaymentResponse pgResponse = pgPaymentGateway.getPaymentStatus(
                String.valueOf(payment.orderId()), String.valueOf(payment.userId())
            );
            if (pgResponse != null && pgResponse.transactionKey() != null) {
                payment.assignTransactionKey(pgResponse.transactionKey());
                applyPgResult(payment, pgResponse.status(), pgResponse.failureReason());
            }
        } catch (Exception e) {
            log.warn("고아 결제 복구 실패 - orderId: {}, reason: {}", payment.orderId(), e.getMessage());
        }
        return PaymentInfo.from(payment);
    }

    private void applyPgResult(PaymentModel payment, String status, String failureReason) {
        OrderModel order = orderService.getOrderForAdmin(payment.orderId());
        if ("SUCCESS".equals(status)) {
            payment.markSuccess();
            order.confirmPayment();
        } else if ("FAILED".equals(status)) {
            payment.markFailed(failureReason);
            order.failPayment();
        }
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPayment(Long paymentId) {
        return PaymentInfo.from(paymentService.getById(paymentId));
    }

    @Transactional(readOnly = true)
    public List<PaymentInfo> getPaymentsByOrderId(Long orderId) {
        return paymentService.getByOrderId(orderId).stream()
            .map(PaymentInfo::from)
            .toList();
    }
}
