package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(summary = "주문 결제 요청", description = "주문에 대해 PG 결제를 요청합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> pay(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        PaymentV1Dto.PayRequest request
    );

    @Operation(summary = "내 결제 조회", description = "주문 ID로 내 결제 상태를 조회합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> getMyPayment(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        @Parameter(description = "주문 ID", required = true) Long orderId
    );

    @Operation(summary = "결제 상태 동기화", description = "PG 조회를 통해 결제 상태를 동기화합니다.")
    ApiResponse<PaymentV1Dto.PaymentResponse> syncMyPayment(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        @Parameter(description = "주문 ID", required = true) Long orderId
    );

    @Operation(summary = "PG 결제 콜백", description = "PG 비동기 처리 결과 콜백을 수신합니다.")
    ApiResponse<Object> callback(PaymentV1Dto.CallbackRequest request);
}
