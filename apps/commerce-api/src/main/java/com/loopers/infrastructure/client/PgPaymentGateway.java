package com.loopers.infrastructure.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PgPaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(PgPaymentGateway.class);

    private final PgClient pgClient;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pgRetry")
    public Optional<PgPaymentDto.TransactionResponse> requestPayment(String userId, PgPaymentDto.PaymentRequest request) {
        return Optional.of(pgClient.requestPayment(userId, request));
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getTransactionFallback")
    @Retry(name = "pgRetry")
    public Optional<PgPaymentDto.TransactionDetailResponse> getTransaction(String userId, String transactionKey) {
        return Optional.of(pgClient.getTransaction(userId, transactionKey));
    }

    private Optional<PgPaymentDto.TransactionResponse> requestPaymentFallback(String userId, PgPaymentDto.PaymentRequest request, Throwable t) {
        log.warn("PG 결제 요청 실패 - fallback 처리. orderId={}, cause={}", request.orderId(), t.getMessage());
        return Optional.empty();
    }

    private Optional<PgPaymentDto.TransactionDetailResponse> getTransactionFallback(String userId, String transactionKey, Throwable t) {
        log.warn("PG 상태 조회 실패 - fallback 처리. transactionKey={}, cause={}", transactionKey, t.getMessage());
        return Optional.empty();
    }
}
