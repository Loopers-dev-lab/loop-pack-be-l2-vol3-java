package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.gateway.PaymentCancelCommand;
import com.loopers.domain.payment.gateway.PaymentConfirmCommand;
import com.loopers.domain.payment.gateway.PaymentConfirmResult;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PaymentQueryResult;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGatewayExecutor {

    private final PaymentGatewayRegistry gatewayRegistry;

    public PgConfirmOutcome confirm(Payment payment) {
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        PaymentConfirmCommand command = new PaymentConfirmCommand(
                payment.getPaymentKey(),
                String.valueOf(payment.getOrderId()),
                payment.getAmount().longValue()
        );

        try {
            PaymentConfirmResult result = gateway.confirm(command);
            return result.success()
                    ? new PgConfirmOutcome.Success()
                    : new PgConfirmOutcome.Failed(result.message());
        } catch (ResourceAccessException e) {
            log.warn("PG 결제 승인 타임아웃: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return new PgConfirmOutcome.Timeout();
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.error("PG 결제 승인 실패: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return new PgConfirmOutcome.Failed(e.getMessage());
        }
    }

    public void cancel(Payment payment, String cancelReason) {
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        try {
            gateway.cancel(
                    payment.getPaymentKey(),
                    new PaymentCancelCommand(String.valueOf(payment.getOrderId()), cancelReason, payment.getAmount().longValue()));
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 취소에 실패했습니다. 잠시 후 다시 시도해주세요");
        }
    }

    public PaymentQueryResult query(Payment payment) {
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        try {
            return gateway.query(payment.getPaymentKey());
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요");
        }
    }
}
