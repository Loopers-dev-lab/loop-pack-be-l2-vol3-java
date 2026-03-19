package com.loopers.interfaces.api.payment;

import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "결제 API")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "인증된 회원이 주문에 대한 결제를 요청합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @LoginUser UserInfo loginUser,
            PaymentV1Dto.PaymentRequest request);

    @Operation(summary = "PG 콜백 수신", description = "PG 시스템이 결제 처리 결과를 전달합니다. (인증 없음)")
    ApiResponse<Object> handleCallback(
            PaymentV1Dto.PaymentCallbackRequest request);

    @Operation(summary = "결제 상태 조회", description = "인증된 회원이 주문의 결제 상태를 조회합니다. (프론트엔드 폴링용)")
    ApiResponse<PaymentV1Dto.PaymentResponse> getPaymentStatus(
            @LoginUser UserInfo loginUser,
            Long orderId);
}
