package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Payment V1 API", description = "결제 API (06 §2.1)")
public interface PaymentV1ApiSpec {

    @Operation(
            summary = "결제 요청",
            description = "주문에 대한 결제를 요청합니다. PG 접수 후 비동기로 처리되며, 결과는 콜백으로 수신합니다."
    )
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
            String loginId,
            @Valid PaymentV1Dto.PaymentRequest request
    );
}
