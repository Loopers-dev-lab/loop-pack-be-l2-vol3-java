package com.loopers.infrastructure.payment.pg;

import com.loopers.infrastructure.payment.config.PgFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "pg-client",
    url = "${pg.simulator.url}",
    configuration = PgFeignConfig.class
)
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgTransactionResponse requestPayment(
        @RequestHeader("X-USER-ID") String memberId,
        @RequestBody PgPaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgTransactionDetailResponse getPaymentStatus(
        @RequestHeader("X-USER-ID") String memberId,
        @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgOrderResponse getPaymentByOrderId(
        @RequestHeader("X-USER-ID") String memberId,
        @RequestParam("orderId") String orderId
    );
}
