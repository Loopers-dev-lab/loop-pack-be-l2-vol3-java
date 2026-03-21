package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 PG 결제를 요청합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @Parameter(hidden = true) AuthenticatedUser authUser,
        PaymentV1Dto.PaymentRequest request
    );

    @Operation(summary = "PG 콜백 수신", description = "PG 시스템에서 결제 결과를 전달받습니다.")
    ApiResponse<Void> handleCallback(PaymentV1Dto.PaymentCallbackRequest request);

    @Operation(summary = "결제 상태 동기화 (transactionKey)", description = "transactionKey로 PG에 직접 조회하여 결제 상태를 동기화합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> syncPaymentStatus(
        @Parameter(hidden = true) AuthenticatedUser authUser,
        String transactionKey
    );

    @Operation(summary = "결제 상태 동기화 (orderId)", description = "orderId로 PG에 조회하여 PENDING 결제를 복구합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> syncByOrderId(
        @Parameter(hidden = true) AuthenticatedUser authUser,
        Long orderId
    );
}
