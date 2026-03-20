package com.loopers.infrastructure.pg;

import com.loopers.infrastructure.pg.dto.PgApiResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "pg-command", url = "${pg.base-url}", configuration = PgCommandFeignConfig.class)
public interface PgCommandClient {

    @PostMapping("/api/v1/payments")
    PgApiResponse<PgPaymentResponse> requestPayment(
            @RequestHeader("X-USER-ID") Long userId,
            @RequestBody PgPaymentRequest request
    );
}
