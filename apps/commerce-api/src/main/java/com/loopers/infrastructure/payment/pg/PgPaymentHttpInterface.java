package com.loopers.infrastructure.payment.pg;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@HttpExchange("/api/v1/payments")
public interface PgPaymentHttpInterface {

    @CircuitBreaker(name = "pg-payment")
    @PostExchange
    PgApiResponse<PgTransactionResponse> requestPayment(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody PgPaymentRequest request
    );

    @Retry(name = "pg-query")
    @CircuitBreaker(name = "pg-query")
    @GetExchange("/{transactionKey}")
    PgApiResponse<PgTransactionDetailResponse> getTransaction(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable String transactionKey
    );

    @Retry(name = "pg-query")
    @CircuitBreaker(name = "pg-query")
    @GetExchange
    PgApiResponse<PgOrderResponse> getTransactionsByOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam("orderId") String orderId
    );
}
