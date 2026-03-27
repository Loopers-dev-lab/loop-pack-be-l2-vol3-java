package com.loopers.interfaces.api.payment;

import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Payment V1 API", description = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 요청합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        MemberModel member, PaymentV1Dto.PaymentRequest request
    );

    @Operation(summary = "결제 상태 조회", description = "결제 상세 정보를 조회합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> getPayment(
        MemberModel member, Long paymentId
    );

    @Operation(summary = "주문별 결제 내역 조회", description = "주문에 대한 모든 결제 시도를 조회합니다.")
    ApiResponse<List<PaymentV1Dto.PaymentResponse>> getPaymentsByOrderId(
        MemberModel member, Long orderId
    );

    @Operation(summary = "PG 콜백 수신", description = "PG 시스템으로부터 결제 결과를 수신합니다.")
    ApiResponse<Object> handleCallback(PaymentV1Dto.CallbackRequest request);
}
