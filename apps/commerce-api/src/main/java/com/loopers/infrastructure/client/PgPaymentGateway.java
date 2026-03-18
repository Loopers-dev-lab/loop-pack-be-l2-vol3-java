package com.loopers.infrastructure.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PgPaymentGateway {
    private static final Logger log = LoggerFactory.getLogger(PgPaymentGateway.class);

    private final PgClient pgClient;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPaymentFallback")
    public PgPaymentDto.TransactionResponse requestPayment(String userId, PgPaymentDto.PaymentRequest request) {
        PgPaymentDto.ApiResponse<PgPaymentDto.TransactionResponse> response = pgClient.requestPayment(userId, request);
        if (!response.isSuccess()) {
            throw new PgPaymentException("PG 결제 요청 실패: " + (response.meta() != null ? response.meta().message() : "unknown"));
        }
        if (response.data() == null) {
            throw new PgPaymentException("PG 결제 요청 응답 데이터가 없습니다.");
        }
        return response.data();
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getTransactionFallback")
    @Retry(name = "pgRetry")
    public PgPaymentDto.TransactionDetailResponse getTransaction(String userId, String transactionKey) {
        PgPaymentDto.ApiResponse<PgPaymentDto.TransactionDetailResponse> response = pgClient.getTransaction(userId, transactionKey);
        if (!response.isSuccess()) {
            throw new PgPaymentException("PG 거래 조회 실패: " + (response.meta() != null ? response.meta().message() : "unknown"));
        }
        if (response.data() == null) {
            throw new PgPaymentException("PG 거래 조회 응답 데이터가 없습니다.");
        }
        return response.data();
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getTransactionsByOrderFallback")
    @Retry(name = "pgRetry")
    public PgPaymentDto.OrderTransactionResponse getTransactionsByOrder(String userId, String pgOrderCode) {
        PgPaymentDto.ApiResponse<PgPaymentDto.OrderTransactionResponse> response = pgClient.getTransactionsByOrderCode(userId, pgOrderCode);
        if (!response.isSuccess()) {
            throw new PgPaymentException("PG 주문 거래 조회 실패: " + (response.meta() != null ? response.meta().message() : "unknown"));
        }
        if (response.data() == null) {
            throw new PgPaymentException("PG 주문 거래 조회 응답 데이터가 없습니다.");
        }
        return response.data();
    }

    private PgPaymentDto.TransactionResponse requestPaymentFallback(String userId, PgPaymentDto.PaymentRequest request, Throwable t) {
        log.warn("PG 결제 요청 실패 - fallback 처리. orderId={}, cause={}", request.orderId(), t.getMessage());
        throw new PgPaymentException("PG 결제 요청 불가: " + t.getMessage());
    }

    private PgPaymentDto.TransactionDetailResponse getTransactionFallback(String userId, String transactionKey, Throwable t) {
        log.warn("PG 상태 조회 실패 - fallback 처리. transactionKey={}, cause={}", transactionKey, t.getMessage());
        throw new PgPaymentException("PG 거래 조회 불가: " + t.getMessage());
    }

    private PgPaymentDto.OrderTransactionResponse getTransactionsByOrderFallback(String userId, String pgOrderCode, Throwable t) {
        log.warn("PG 주문 거래 조회 실패 - fallback 처리. pgOrderCode={}, cause={}", pgOrderCode, t.getMessage());
        throw new PgPaymentException("PG 주문 거래 조회 불가: " + t.getMessage());
    }
}
