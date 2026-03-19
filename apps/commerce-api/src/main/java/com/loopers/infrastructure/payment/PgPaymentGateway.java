package com.loopers.infrastructure.payment;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class PgPaymentGateway {

    private final PgClient pgClient;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pgRetry")
    public PgPaymentResponse requestPayment(String userId, PgPaymentRequest request) {
        return pgClient.requestPayment(userId, request);
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getPaymentStatusFallback")
    @Retry(name = "pgRetry")
    public PgPaymentStatusResponse getPaymentStatus(String userId, String transactionId) {
        return pgClient.getPaymentStatus(userId, transactionId);
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getPaymentByOrderIdFallback")
    @Retry(name = "pgRetry")
    public PgOrderResponse getPaymentByOrderId(String userId, String orderId) {
        return pgClient.getPaymentByOrderId(userId, orderId);
    }

    // 결제 요청 fallback — PG 장애/타임아웃 시 FAIL 응답 반환
    private PgPaymentResponse requestPaymentFallback(String userId, PgPaymentRequest request, Throwable t) {
        log.warn("PG 결제 요청 fallback 실행: userId={}, orderId={}, reason={}",
                userId, request.orderId(), t.getMessage());
        return new PgPaymentResponse(
                new PgPaymentResponse.Meta("FAIL", "PG_UNAVAILABLE", "결제 시스템 일시 장애"),
                null
        );
    }

    // 상태 조회 fallback — PG 장애 시 조회 불가 응답 반환
    private PgPaymentStatusResponse getPaymentStatusFallback(String userId, String transactionId, Throwable t) {
        log.warn("PG 상태 조회 fallback 실행: userId={}, transactionId={}, reason={}",
                userId, transactionId, t.getMessage());
        return new PgPaymentStatusResponse(
                new PgPaymentStatusResponse.Meta("FAIL", "PG_UNAVAILABLE", "결제 시스템 일시 장애"),
                null
        );
    }

    // orderId 기반 조회 fallback
    private PgOrderResponse getPaymentByOrderIdFallback(String userId, String orderId, Throwable t) {
        log.warn("PG 주문 조회 fallback 실행: userId={}, orderId={}, reason={}",
                userId, orderId, t.getMessage());
        return new PgOrderResponse(
                new PgOrderResponse.Meta("FAIL", "PG_UNAVAILABLE", "결제 시스템 일시 장애"),
                null
        );
    }
}
