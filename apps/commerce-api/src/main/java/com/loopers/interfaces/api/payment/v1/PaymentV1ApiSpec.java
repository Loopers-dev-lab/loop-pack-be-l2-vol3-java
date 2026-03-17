package com.loopers.interfaces.api.payment.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(
            summary = "결제 생성 API",
            description = "주문에 대한 결제를 생성합니다."
    )
    ApiResponse<PaymentDto.CreatePaymentResponse> createPayment(Long userId, PaymentDto.CreatePaymentRequest request);

    @Operation(
            summary = "결제 콜백 수신 API",
            description = "PG로부터 결제 결과를 수신합니다."
    )
    ApiResponse<Void> handlePaymentCallback(PaymentDto.PaymentCallbackRequest request);
}
