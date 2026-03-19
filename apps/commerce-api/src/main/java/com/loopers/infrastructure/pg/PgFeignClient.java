package com.loopers.infrastructure.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "pg-client",
        url = "${pg.base-url}"
)
public interface PgFeignClient {

    @PostMapping("/api/v1/payments")
    PgFeignPaymentResponse requestPayment(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody PgFeignPaymentRequest request
    );

    @GetMapping("/api/v1/payments")
    PgFeignPaymentStatusResponse getPaymentStatus(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam("orderId") Long orderId
    );
}
