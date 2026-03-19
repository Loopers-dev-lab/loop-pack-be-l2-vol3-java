package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.TossPaymentClient;
import com.loopers.infrastructure.payment.dto.TossCancelRequest;
import com.loopers.infrastructure.payment.dto.TossConfirmRequest;
import com.loopers.infrastructure.payment.dto.TossPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final TossPaymentClient tossClient;

    // Command

    @Bulkhead(name = "pg-payment", fallbackMethod = "paymentBulkheadFallback")
    public PaymentInfo requestPayment(Long userId, PaymentCommand.Request command) {
        // TX1: 주문 검증 + 중복 결제 확인 + Payment 생성 (PENDING)
        BigDecimal finalAmount = validateOrder(userId, command.orderId());
        Payment payment = paymentService.createPayment(
                command.orderId(), userId, command.cardType(), command.cardNo(), finalAmount);

        // 토스 confirm 호출 (트랜잭션 밖 — DB 커넥션 미점유)
        confirmPaymentWithToss(payment);

        // 최종 상태 조회
        Payment updatedPayment = paymentService.getPayment(payment.getId());
        return PaymentInfo.from(updatedPayment);
    }

    public PaymentInfo cancelPayment(Long userId, Long paymentId, PaymentCommand.Cancel command) {
        Payment payment = paymentService.getPayment(paymentId);
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다");
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 결제 상태입니다");
        }

        // 토스 취소 API 호출
        tossClient.cancelPayment(
                payment.getPaymentKey(),
                new TossCancelRequest(command.cancelReason(), command.cancelAmount()));

        // TX2: DB 상태 업데이트
        paymentService.markCanceled(paymentId, command.cancelReason());

        return PaymentInfo.from(paymentService.getPayment(paymentId));
    }

    @Bulkhead(name = "pg-payment", fallbackMethod = "verifyBulkheadFallback")
    public PaymentInfo verifyPayment(Long userId, Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다");
        }
        if (payment.isFinalized()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 확정된 결제입니다");
        }

        // PENDING 상태: 토스에서 상태 조회
        TossPaymentResponse response;
        try {
            response = tossClient.getPayment(payment.getPaymentKey());
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
        }

        if (response != null && response.isDone()) {
            confirmPaymentResult(payment.getId());
        } else {
            paymentService.markFailed(payment.getId(), "결제 미완료");
        }

        return PaymentInfo.from(paymentService.getPayment(payment.getId()));
    }

    @Transactional
    public void confirmPaymentResult(Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        if (payment.isFinalized()) {
            return;
        }
        payment.markSucceeded();
        orderService.payOrder(payment.getOrderId());
    }

    // Query

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentDetail(Long userId, Long paymentId) {
        Payment payment = paymentService.getPayment(paymentId);
        if (!payment.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 결제입니다");
        }
        return PaymentInfo.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentInfo getPaymentByOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }
        return paymentService.getLatestPaymentByOrderId(orderId)
                .map(PaymentInfo::from)
                .orElse(PaymentInfo.empty(orderId));
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

    private BigDecimal validateOrder(Long userId, Long orderId) {
        Order order = orderService.getOrder(orderId);
        if (!order.isOwnedBy(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제할 수 없는 주문 상태입니다");
        }
        if (paymentService.existsActivePayment(orderId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중이거나 완료된 주문입니다");
        }
        return order.getFinalAmount();
    }

    private void confirmPaymentWithToss(Payment payment) {
        TossConfirmRequest confirmRequest = new TossConfirmRequest(
                payment.getPaymentKey(),
                String.valueOf(payment.getOrderId()),
                payment.getAmount().longValue()
        );

        try {
            TossPaymentResponse response = tossClient.confirmPayment(confirmRequest);
            if (response != null && response.isDone()) {
                // TX2: 결제 성공 + 주문 상태 전이
                confirmPaymentResult(payment.getId());
            } else {
                paymentService.markFailed(payment.getId(), "PG 승인 실패");
                throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요");
            }
        } catch (ResourceAccessException e) {
            log.warn("토스 결제 승인 타임아웃: paymentId={}, message={}", payment.getId(), e.getMessage());
            // PENDING 상태 유지 (verify로 확인)
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.error("토스 결제 승인 실패: paymentId={}, message={}", payment.getId(), e.getMessage());
            paymentService.markFailed(payment.getId(), e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요");
        }
    }
}
