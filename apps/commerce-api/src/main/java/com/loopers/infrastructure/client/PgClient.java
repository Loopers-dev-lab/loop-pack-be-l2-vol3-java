package com.loopers.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "pg-client", url = "${pg.simulator.url}")
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgPaymentDto.TransactionResponse requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PgPaymentDto.PaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgPaymentDto.TransactionDetailResponse getTransaction(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable("transactionKey") String transactionKey
    );
}
