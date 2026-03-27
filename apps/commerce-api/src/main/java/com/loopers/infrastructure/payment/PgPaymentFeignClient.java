package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.OrderTransactionsData;
import com.loopers.infrastructure.payment.dto.PGPaymentRequest;
import com.loopers.infrastructure.payment.dto.PaymentData;
import com.loopers.infrastructure.payment.dto.TransactionDetailData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "pg-payment", url = "${pg-simulator.base-url}", configuration = PgFeignConfig.class)
public interface PgPaymentFeignClient {

    @PostMapping("/api/v1/payments")
    PgApiResponse<PaymentData> requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PGPaymentRequest request);

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgApiResponse<TransactionDetailData> getPaymentStatus(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable String transactionKey);

    @GetMapping("/api/v1/payments")
    PgApiResponse<OrderTransactionsData> getPaymentsByOrder(
            @RequestHeader("X-USER-ID") String userId,
            @RequestParam("orderId") String orderId);
}
