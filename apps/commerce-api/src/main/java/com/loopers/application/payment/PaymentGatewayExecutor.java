package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.gateway.PaymentGateway;
import com.loopers.domain.payment.gateway.PgBusinessException;
import com.loopers.domain.payment.gateway.PgCommand;
import com.loopers.domain.payment.gateway.PgCommunicationException;
import com.loopers.domain.payment.gateway.PgResult;
import com.loopers.domain.payment.gateway.PgTimeoutException;
import com.loopers.domain.payment.gateway.PgUnavailableException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentGatewayExecutor {

    private final PaymentGatewayRegistry gatewayRegistry;

    public PgConfirmOutcome confirm(Payment payment) {
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        PgCommand.Confirm command = PgCommand.Confirm.of(
                payment.getPaymentKey(),
                String.valueOf(payment.getOrderId()),
                payment.getAmount().longValue()
        );

        try {
            PgResult.Confirm result = gateway.confirm(command);
            if (result.success()) {
                if (!isAmountMatch(payment, result.amount())) {
                    log.error("PG 결제 금액 불일치: paymentId={}, expected={}, actual={}",
                            payment.getId(), payment.getAmount(), result.amount());
                    return new PgConfirmOutcome.AmountMismatch(result.amount());
                }
                return new PgConfirmOutcome.Success(result.amount());
            }
            return new PgConfirmOutcome.Failed(result.message());
        } catch (PgTimeoutException e) {
            log.warn("PG 결제 승인 타임아웃: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return new PgConfirmOutcome.Timeout();
        } catch (PgBusinessException e) {
            log.warn("PG 결제 승인 거절: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return new PgConfirmOutcome.Failed(e.getMessage());
        } catch (PgUnavailableException e) {
            log.warn("PG 서비스 불가: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return new PgConfirmOutcome.Unavailable();
        } catch (PgCommunicationException e) {
            log.error("PG 결제 승인 통신 실패: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return new PgConfirmOutcome.Failed(e.getMessage());
        } catch (Exception e) {
            log.error("PG 결제 승인 실패: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return new PgConfirmOutcome.Failed(e.getMessage());
        }
    }

    public boolean cancel(Payment payment, String cancelReason) {
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        try {
            gateway.cancel(
                    payment.getPaymentKey(),
                    PgCommand.Cancel.of(String.valueOf(payment.getOrderId()), cancelReason, payment.getAmount().longValue()));
            return true;
        } catch (Exception e) {
            log.warn("PG 결제 취소 실패: paymentId={}, pgType={}, message={}",
                    payment.getId(), gateway.getType(), e.getMessage());
            return false;
        }
    }

    public PgQueryOutcome queryOutcome(Payment payment) {
        PgResult.Query result = query(payment);
        if (!result.found() || !result.done()) {
            return new PgQueryOutcome.NotConfirmed();
        }
        if (!isAmountMatch(payment, result.amount())) {
            log.error("PG 조회 금액 불일치: paymentId={}, expected={}, actual={}",
                    payment.getId(), payment.getAmount(), result.amount());
            return new PgQueryOutcome.AmountMismatch(result.amount());
        }
        return new PgQueryOutcome.Confirmed(result.amount());
    }

    private PgResult.Query query(Payment payment) {
        PaymentGateway gateway = gatewayRegistry.getGateway(payment.getPgType());
        try {
            return gateway.query(payment.getPaymentKey());
        } catch (Exception e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요", e);
        }
    }

    private boolean isAmountMatch(Payment payment, Long pgAmount) {
        if (pgAmount == null) {
            log.error("PG 응답에 금액 정보 없음: paymentId={}", payment.getId());
            return false;
        }
        BigDecimal expected = payment.getAmount();
        BigDecimal actual = BigDecimal.valueOf(pgAmount);
        return expected.compareTo(actual) == 0;
    }
}
