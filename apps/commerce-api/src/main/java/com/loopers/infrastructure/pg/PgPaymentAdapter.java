package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentCommand;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentInfo;
import com.loopers.infrastructure.pg.dto.PgApiResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PgPaymentAdapter implements PaymentGateway {

    private final PgCommandClient pgCommandClient;
    private final PgQueryClient pgQueryClient;

    @Override
    @CircuitBreaker(name = "pg-command", fallbackMethod = "requestPaymentFallback")
    public PaymentInfo requestPayment(Long userId, PaymentCommand.PgRequest command) {
        PgPaymentRequest request = toRequest(command);
        PgApiResponse<PgPaymentResponse> response = pgCommandClient.requestPayment(userId, request);
        return toInfo(response.data());
    }

    @Override
    @CircuitBreaker(name = "pg-query", fallbackMethod = "getPaymentFallback")
    @Retry(name = "pg-query")
    public PaymentInfo getPayment(Long userId, String transactionKey) {
        PgApiResponse<PgPaymentResponse> response = pgQueryClient.getPayment(userId, transactionKey);
        return toInfo(response.data());
    }

    @Override
    @CircuitBreaker(name = "pg-query", fallbackMethod = "getPaymentByOrderIdFallback")
    @Retry(name = "pg-query")
    public PaymentInfo getPaymentByOrderId(Long userId, String orderId) {
        PgApiResponse<PgPaymentResponse> response = pgQueryClient.getPaymentByOrderId(userId, orderId);
        return toInfo(response.data());
    }

    private PaymentInfo requestPaymentFallback(Long userId, PaymentCommand.PgRequest command, Throwable t) {
        return PaymentInfo.empty();
    }

    private PaymentInfo getPaymentFallback(Long userId, String transactionKey, Throwable t) {
        return PaymentInfo.empty();
    }

    private PaymentInfo getPaymentByOrderIdFallback(Long userId, String orderId, Throwable t) {
        return PaymentInfo.empty();
    }

    private PgPaymentRequest toRequest(PaymentCommand.PgRequest command) {
        return new PgPaymentRequest(
                command.orderId(),
                command.cardType(),
                command.cardNo(),
                command.amount(),
                command.callbackUrl()
        );
    }

    private PaymentInfo toInfo(PgPaymentResponse response) {
        return new PaymentInfo(
                response.transactionKey(),
                response.orderId(),
                response.cardType(),
                response.cardNo(),
                response.amount(),
                response.status()
        );
    }
}
