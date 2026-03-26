package com.loopers.infrastructure.payment;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pgSimulator", url = "${pg.simulator.url}", configuration = PgSimulatorFeignConfig.class)
public interface PgSimulatorClient {

    @PostMapping("/api/v1/payments")
    void requestPayment(@RequestBody PgSimulatorClient.PgPaymentRequest request);

    @GetMapping("/api/v1/payments")
    List<PgSimulatorClient.PgPaymentResponse> getPaymentByOrderId(@RequestParam("orderId") String orderId);

    record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        String amount,
        String callbackUrl
    ) {}

    record PgPaymentResponse(
        String transactionId,
        boolean success
    ) {}
}
