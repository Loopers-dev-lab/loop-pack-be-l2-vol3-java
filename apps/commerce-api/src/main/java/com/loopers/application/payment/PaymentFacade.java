package com.loopers.application.payment;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.Payment;
import com.loopers.infrastructure.payment.PgClient;
import com.loopers.infrastructure.payment.PgProperties;
import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFacade {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final PgClient pgClient;
    private final PgProperties pgProperties;

    // Command

    public PaymentInfo requestPayment(Long userId, PaymentCommand.Request command) {
        // 1. 트랜잭션: 주문 검증 + 중복 결제 확인 + Payment 생성
        BigDecimal finalAmount = validateOrder(userId, command.orderId());
        Payment payment = paymentService.createPayment(
                command.orderId(), userId, command.cardType(), command.cardNo(), finalAmount);

        // 2. 트랜잭션 밖: PG 호출
        requestPaymentToPg(payment, userId);

        // 3. 최종 상태 조회
        Payment updatedPayment = paymentService.getPayment(payment.getId());
        return PaymentInfo.from(updatedPayment);
    }

    @Transactional
    public void handleCallback(PaymentCommand.Callback command) {
        Optional<Payment> optPayment = paymentService.getPaymentByTransactionKey(command.transactionKey());
        if (optPayment.isEmpty()) {
            return;
        }

        Payment payment = optPayment.get();
        if (payment.isFinalized()) {
            return;
        }

        if (command.isSuccess()) {
            payment.markSucceeded(command.transactionKey());
            orderService.payOrder(payment.getOrderId());
        } else {
            payment.markFailed(command.reason());
        }
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

    private void requestPaymentToPg(Payment payment, Long userId) {
        PgPaymentRequest pgRequest = PgPaymentRequest.of(
                payment.getOrderId(),
                payment.getCardType().name(),
                payment.getCardNo(),
                payment.getAmount().longValue(),
                pgProperties.callbackUrl()
        );

        try {
            PgPaymentResponse pgResponse = pgClient.requestPayment(userId, pgRequest);
            if (pgResponse != null && pgResponse.isSuccess() && pgResponse.data() != null) {
                paymentService.markInProgress(payment.getId(), pgResponse.data().transactionKey());
            } else {
                String reason = (pgResponse != null && pgResponse.meta() != null)
                        ? pgResponse.meta().message()
                        : "PG 응답 오류";
                paymentService.markFailed(payment.getId(), reason);
                throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요");
            }
        } catch (ResourceAccessException e) {
            log.warn("PG 요청 타임아웃: paymentId={}, message={}", payment.getId(), e.getMessage());
            // PENDING 상태 유지 (보류)
        } catch (RestClientException e) {
            log.error("PG 요청 실패: paymentId={}, message={}", payment.getId(), e.getMessage());
            paymentService.markFailed(payment.getId(), e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 요청에 실패했습니다. 잠시 후 다시 시도해주세요");
        }
    }
}
