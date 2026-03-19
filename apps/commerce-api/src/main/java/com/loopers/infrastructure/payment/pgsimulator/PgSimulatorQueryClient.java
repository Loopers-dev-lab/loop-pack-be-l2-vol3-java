package com.loopers.infrastructure.payment.pgsimulator;

import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorApiResponse;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorOrderTransactionsResponse;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorTransactionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        contextId = "pgSimulatorQueryClient",
        name = "pgSimulator",
        url = "${loopers.payment.pg-simulator.url}"
)
public interface PgSimulatorQueryClient {

    @GetMapping("/api/v1/payments/{transactionKey}")
    PgSimulatorApiResponse<PgSimulatorTransactionResponse> getPayment(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable("transactionKey") String transactionKey
    );

    @GetMapping("/api/v1/payments")
    PgSimulatorApiResponse<PgSimulatorOrderTransactionsResponse> getPaymentsByOrderId(
            @RequestHeader("X-USER-ID") String userId,
            @RequestParam("orderId") String orderId
    );
}
