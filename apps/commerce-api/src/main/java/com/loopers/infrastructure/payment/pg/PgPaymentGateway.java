package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PgResult;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private final PgClient pgClient;
    private final MeterRegistry meterRegistry;

    @Bulkhead(name = "pg-request")
    @CircuitBreaker(name = "pg-request", fallbackMethod = "requestPaymentFallback")
    @Override
    public PgResult requestPayment(PaymentModel payment, String callbackUrl) {
        PgTransactionResponse response = pgClient.requestPayment(
            String.valueOf(payment.getRefMemberId()),
            new PgPaymentRequest(
                String.valueOf(payment.getRefOrderId().value()),
                payment.getCardType(),
                payment.getCardNumber(),
                payment.getAmount().longValue(),
                callbackUrl
            )
        );
        return new PgResult(response.transactionKey(), response.status(), null, null);
    }

    PgResult requestPaymentFallback(PaymentModel payment, String callbackUrl, CallNotPermittedException e) {
        log.warn("PG CB Open — 결제 요청 차단: orderId={}", payment.getRefOrderId().value());
        meterRegistry.counter("payment.gateway.fallback", "reason", "cb_open").increment();
        return PgResult.unavailable();
    }

    PgResult requestPaymentFallback(PaymentModel payment, String callbackUrl, IOException e) {
        log.error("PG 네트워크 오류 — 결제 요청 실패: orderId={}", payment.getRefOrderId().value(), e);
        meterRegistry.counter("payment.gateway.fallback", "reason", "io_error").increment();
        return PgResult.unavailable();
    }

    PgResult requestPaymentFallback(PaymentModel payment, String callbackUrl, Throwable t) {
        log.error("PG 오류 — 결제 요청 실패: orderId={}", payment.getRefOrderId().value(), t);
        String reason = t instanceof BulkheadFullException ? "bulkhead_full" : "unknown";
        meterRegistry.counter("payment.gateway.fallback", "reason", reason).increment();
        return PgResult.unavailable();
    }

    @CircuitBreaker(name = "pg-query", fallbackMethod = "getPaymentResultFallback")
    @Retry(name = "pg-query")
    @Override
    public PgResult getPaymentResult(String pgTransactionId, Long memberId) {
        PgTransactionDetailResponse response = pgClient.getPaymentStatus(
            String.valueOf(memberId), pgTransactionId
        );
        return new PgResult(response.transactionKey(), response.status(), response.reason(),
            response.amount() != null ? java.math.BigDecimal.valueOf(response.amount()) : null);
    }

    PgResult getPaymentResultFallback(String pgTransactionId, Long memberId, Throwable t) {
        log.warn("PG 조회 실패 (CB Open 또는 Retry 소진): transactionId={}", pgTransactionId);
        return PgResult.unavailable();
    }

    @CircuitBreaker(name = "pg-query", fallbackMethod = "getPaymentByOrderIdFallback")
    @Retry(name = "pg-query")
    @Override
    public PgResult getPaymentByOrderId(Long orderId, Long memberId) {
        PgOrderResponse response = pgClient.getPaymentByOrderId(
            String.valueOf(memberId), String.valueOf(orderId)
        );
        List<PgTransactionResponse> transactions = response.transactions();
        if (transactions.isEmpty()) {
            return PgResult.unavailable();
        }
        PgTransactionResponse tx = transactions.get(0);
        return new PgResult(tx.transactionKey(), tx.status(), null, null);
    }

    PgResult getPaymentByOrderIdFallback(Long orderId, Long memberId, Throwable t) {
        log.warn("PG 주문 조회 실패 (CB Open 또는 Retry 소진): orderId={}", orderId);
        return PgResult.unavailable();
    }
}
