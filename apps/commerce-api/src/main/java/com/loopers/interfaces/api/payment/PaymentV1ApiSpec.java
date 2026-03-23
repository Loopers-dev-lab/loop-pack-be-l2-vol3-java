package com.loopers.interfaces.api.payment;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.payment.dto.CreatePaymentApiReqDto;
import com.loopers.interfaces.api.payment.dto.FindPaymentApiResDto;
import com.loopers.interfaces.api.payment.dto.PaymentCallbackApiReqDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment V1 API", description = "결제 관리 API 입니다.")
public interface PaymentV1ApiSpec {

    @Operation(summary = "결제 요청", description = "주문에 대한 결제를 요청합니다.")
    ApiResponse<FindPaymentApiResDto> createPayment(
            @Parameter(description = "로그인 ID") String loginId,
            @Parameter(description = "로그인 비밀번호") String password,
            CreatePaymentApiReqDto request
    );

    @Operation(summary = "결제 콜백", description = "PG 시스템으로부터 결제 결과를 수신합니다.")
    ApiResponse<Void> handleCallback(PaymentCallbackApiReqDto request);

    @Operation(summary = "결제 상태 확인", description = "결제 상태를 확인하고 PG와 동기화합니다.")
    ApiResponse<FindPaymentApiResDto> checkPaymentStatus(
            @Parameter(description = "로그인 ID") String loginId,
            @Parameter(description = "로그인 비밀번호") String password,
            @Parameter(description = "주문 ID") String orderId
    );
}
