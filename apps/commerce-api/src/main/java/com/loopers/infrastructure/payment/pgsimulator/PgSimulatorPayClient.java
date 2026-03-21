package com.loopers.infrastructure.payment.pgsimulator;

import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorApiResponse;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorRequestPaymentRequest;
import com.loopers.infrastructure.payment.pgsimulator.dto.PgSimulatorTransactionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        contextId = "pgSimulatorPayClient",
        name = "pgSimulator",
        url = "${loopers.payment.pg-simulator.url}"
)
public interface PgSimulatorPayClient {

    @PostMapping("/api/v1/payments")
    PgSimulatorApiResponse<PgSimulatorTransactionResponse> requestPayment(
            @RequestHeader("X-USER-ID") String userId,
            @RequestBody PgSimulatorRequestPaymentRequest request
    );
}
