package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * PG-Simulator HTTP 클라이언트 (06 §10.3).
 * Phase 1: 뼈대만 생성. Timeout은 feign.client.config.default 적용.
 * Phase 2~: PaymentFacade에서 트랜잭션 밖에서 호출.
 */
@FeignClient(
        name = "pgSimulator",
        url = "${pg.simulator.url}"
)
public interface PgSimulatorClient {

    /**
     * 결제 요청 접수 (비동기). 1~5초 후 콜백으로 결과 수신 (06 §2.2).
     */
    @PostMapping("/api/v1/payments")
    PgSimulatorResponse requestPayment(@RequestBody PgSimulatorRequest request);

    /**
     * 결제 정보 조회 (폴링/복구용).
     */
    @GetMapping("/api/v1/payments/{paymentId}")
    Object getPaymentStatus(@PathVariable("paymentId") String paymentId);

    /**
     * 주문별 결제 정보 조회 (폴링/복구용).
     */
    @GetMapping(value = "/api/v1/payments", params = "orderId")
    Object getPaymentsByOrderId(@RequestParam("orderId") Long orderId);
}
