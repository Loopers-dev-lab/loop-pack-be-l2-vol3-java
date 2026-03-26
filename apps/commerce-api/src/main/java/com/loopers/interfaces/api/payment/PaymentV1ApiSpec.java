package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "Payment V1 API", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제", description = "주문에 대한 결제를 처리한다.")
    ApiResponse<PaymentV1Dto.PayResponse> pay(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestBody PaymentV1Dto.PayRequest request
    );
}