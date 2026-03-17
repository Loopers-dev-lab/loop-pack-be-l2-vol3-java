package com.loopers.interfaces.api.payment;

import com.loopers.infrastructure.payment.dto.PgCallbackPayload;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment API", description = "결제 API")
public interface PaymentApiV1Spec {

    // Command

    @Operation(summary = "결제 요청", description = "주문에 대한 PG 카드 결제를 요청한다")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            AuthenticatedUser user,
            PaymentRequest.Request request
    );

    @Operation(summary = "결제 콜백 수신", description = "PG 시스템이 결제 결과를 콜백으로 전달한다")
    void handleCallback(PgCallbackPayload payload);

    // Query

    @Operation(summary = "결제 상세 조회", description = "단일 결제의 상태를 확인한다")
    ApiResponse<PaymentV1Dto.PaymentResponse> getPaymentDetail(
            AuthenticatedUser user,
            Long paymentId
    );
}
