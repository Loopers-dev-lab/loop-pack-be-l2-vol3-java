package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.PGPaymentRequest;
import com.loopers.infrastructure.payment.dto.PaymentData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "pg-payment2",
        url = "${pg2-simulator.base-url:http://localhost:8083}",
        configuration = PgFeignConfig.class)
public interface Pg2PaymentFeignClient {

    @PostMapping("/api/v1/payments")
    PgApiResponse<PaymentData> requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PGPaymentRequest request);
}
