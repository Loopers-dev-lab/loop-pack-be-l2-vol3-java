package com.loopers.infrastructure.pg.simulator;

import com.loopers.infrastructure.pg.PgPaymentRequest;
import com.loopers.infrastructure.pg.PgPaymentResponse;
import com.loopers.infrastructure.pg.PgPaymentStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = "pg-simulator",
    url = "${pg.simulator.url}",
    configuration = SimulatorFeignConfig.class
)
public interface SimulatorFeignClient {

    @PostMapping("/api/v1/payments")
    PgPaymentResponse requestPayment(@RequestBody PgPaymentRequest request);

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgPaymentStatusResponse getPaymentStatus(@PathVariable("transactionKey") String transactionKey);

    @GetMapping("/api/v1/payments")
    PgPaymentStatusResponse getPaymentByOrderId(@RequestParam("orderId") String orderId);
}
