package com.loopers.interfaces.api.payment.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "대고객 결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(
            summary = "결제 생성 API",
            description = "주문에 대한 결제를 생성합니다."
    )
    ApiResponse<PaymentDto.CreatePaymentResponse> createPayment(Long userId, PaymentDto.CreatePaymentRequest request);
}
