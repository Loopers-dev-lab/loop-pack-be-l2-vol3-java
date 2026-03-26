package com.loopers.infrastructure.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "pg-payment", url = "${pg.base-url}", configuration = PgPaymentClientConfig.class)
public interface PgPaymentClient {

    @PostMapping("/api/v1/payments")
    PgPaymentResponse<PgPaymentResponse.TransactionResponse> requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PgPaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgPaymentResponse<PgPaymentResponse.TransactionDetailResponse> getTransaction(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgPaymentResponse<PgPaymentResponse.OrderResponse> getTransactionsByOrder(
            @RequestHeader("X-USER-ID") String userId,
            @RequestParam("orderId") String orderId
    );
}
