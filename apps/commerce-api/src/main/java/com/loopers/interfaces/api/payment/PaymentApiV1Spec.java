package com.loopers.interfaces.api.payment;

import com.loopers.domain.payment.gateway.PgType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Payment API", description = "결제 API")
public interface PaymentApiV1Spec {

    // Command

    @Operation(summary = "결제 요청", description = "주문에 대한 PG 카드 결제를 요청한다")
    ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            AuthenticatedUser user,
            PaymentRequest.Request request
    );

    @Operation(summary = "결제 취소", description = "결제 성공 건을 취소한다")
    ApiResponse<PaymentV1Dto.PaymentResponse> cancelPayment(
            AuthenticatedUser user,
            Long paymentId,
            PaymentRequest.Cancel request
    );

    @Operation(summary = "결제 수동 확인", description = "PG에 결제 상태를 수동으로 조회하여 확정한다")
    ApiResponse<PaymentV1Dto.PaymentResponse> verifyPayment(
            AuthenticatedUser user,
            Long paymentId
    );

    // Query

    @Operation(summary = "결제 상세 조회", description = "단일 결제의 상태를 확인한다")
    ApiResponse<PaymentV1Dto.PaymentResponse> getPaymentDetail(
            AuthenticatedUser user,
            Long paymentId
    );

    @Operation(summary = "사용 가능한 결제수단 조회", description = "서킷 상태를 확인하여 가용한 PG 목록을 반환한다")
    ApiResponse<List<PgType>> getAvailableMethods();
}
