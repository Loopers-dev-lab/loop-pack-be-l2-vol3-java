package com.loopers.infrastructure.payment.pg;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/payments")
public interface PgPaymentHttpInterface {

    @PostExchange
    PgApiResponse<PgTransactionResponse> requestPayment(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody PgPaymentRequest request
    );

    @GetExchange("/{transactionKey}")
    PgApiResponse<PgTransactionDetailResponse> getTransaction(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable String transactionKey
    );

    @GetExchange
    PgApiResponse<PgOrderResponse> getTransactionsByOrder(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam("orderId") String orderId
    );
}
