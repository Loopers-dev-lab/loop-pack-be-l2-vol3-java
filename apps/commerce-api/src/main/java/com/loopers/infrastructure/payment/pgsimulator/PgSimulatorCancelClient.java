package com.loopers.infrastructure.payment.pgsimulator;

import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorApiResponse;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorTransactionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        contextId = "pgSimulatorCancelClient",
        name = "pgSimulator",
        url = "${loopers.payment.pg-simulator.url}"
)
public interface PgSimulatorCancelClient {

    @PostMapping("/api/v1/payments/{transactionKey}/cancel")
    PgSimulatorApiResponse<PgSimulatorTransactionResponse> cancelPayment(
            @RequestHeader("X-USER-ID") String userId,
            @PathVariable("transactionKey") String transactionKey
    );
}
