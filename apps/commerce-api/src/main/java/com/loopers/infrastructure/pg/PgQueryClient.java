package com.loopers.infrastructure.pg;

import com.loopers.infrastructure.pg.dto.PgApiResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pg-query", url = "${pg.base-url}", configuration = PgQueryFeignConfig.class)
public interface PgQueryClient {

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgApiResponse<PgPaymentResponse> getPayment(
            @RequestHeader("X-USER-ID") Long userId,
            @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgApiResponse<PgPaymentResponse> getPaymentByOrderId(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam("orderId") String orderId
    );
}
