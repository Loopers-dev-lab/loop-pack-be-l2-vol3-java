package com.loopers.infrastructure.pg.toss;

import com.loopers.infrastructure.pg.PgPaymentResponse;
import com.loopers.infrastructure.pg.PgPaymentStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Toss Sandbox Feign Client.
 *
 * <p>Toss는 동기 PG — confirm 호출 시 즉시 SUCCESS/FAILED 반환.</p>
 *
 * @see <a href="06-resilience-review.md §11.5">Toss API 설계</a>
 */
@FeignClient(
    name = "pg-toss",
    url = "${pg.toss.url}",
    configuration = TossSandboxPgConfig.class
)
public interface TossFeignClient {

    @PostMapping("/v1/payments/confirm")
    PgPaymentResponse confirmPayment(@RequestBody TossConfirmRequest request);

    @GetMapping("/v1/payments/{paymentKey}")
    PgPaymentStatusResponse getPaymentStatus(@PathVariable("paymentKey") String paymentKey);

    @GetMapping("/v1/payments")
    PgPaymentStatusResponse getPaymentByOrderId(@RequestParam("orderId") String orderId);

    record TossConfirmRequest(String orderId, int amount) {}
}
