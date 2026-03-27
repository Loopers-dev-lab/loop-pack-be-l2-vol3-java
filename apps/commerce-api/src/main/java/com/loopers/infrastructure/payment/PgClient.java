package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "pgClient",
        url = "${pg.simulator.url}",
        configuration = PgFeignConfig.class
)
public interface PgClient {

    @PostMapping("/api/v1/payments")
    PgPaymentResponse requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PgPaymentRequest request
    );

    @GetMapping("/api/v1/payments/{transactionId}")
    PgPaymentStatusResponse getPaymentStatus(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable String transactionId
    );

    @GetMapping("/api/v1/payments")
    PgOrderResponse getPaymentByOrderId(
            @RequestHeader("X-USER-ID") String userId,
            @RequestParam String orderId
    );
}
