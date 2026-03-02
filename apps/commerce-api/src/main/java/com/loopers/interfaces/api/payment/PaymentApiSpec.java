package com.loopers.interfaces.api.payment;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Payment API", description = "결제 API")
public interface PaymentApiSpec {

    @Operation(summary = "할인 적용", description = "PENDING 주문에 쿠폰/포인트 할인을 적용합니다.")
    ApiResponse<PaymentResponse.DiscountAppliedResponse> applyDiscount(
            @AuthUser User user, Long orderId, PaymentRequest.ApplyDiscountRequest request);

    @Operation(summary = "결제 요청", description = "PG 결제를 요청합니다. 성공 시 주문이 PAID로 전환됩니다.")
    ApiResponse<PaymentResponse.PaymentResult> pay(
            @AuthUser User user, Long orderId, PaymentRequest.PayRequest request);
}
