package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 요청합니다. PG에 비동기 결제를 요청하고, PENDING 상태로 응답합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @Parameter(description = "로그인 ID") String loginId,
            @Parameter(description = "로그인 비밀번호") String loginPw,
            PaymentV1Dto.PaymentRequest request
    );

    @Operation(summary = "결제 콜백 수신", description = "PG가 결제 처리 완료 후 호출하는 콜백 API 입니다. 인증이 필요하지 않습니다.")
    ApiResponse<Object> handleCallback(
            PaymentV1Dto.CallbackRequest request
    );

    @Operation(summary = "결제 조회 및 상태 동기화", description = "결제 상태를 조회합니다. PENDING/TIMED_OUT 상태인 경우 PG에 상태를 확인하여 동기화합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> getPayment(
            @Parameter(description = "로그인 ID") String loginId,
            @Parameter(description = "로그인 비밀번호") String loginPw,
            @Parameter(description = "결제 ID") Long paymentId
    );
}
