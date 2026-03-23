package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentDto;
import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping("/api/v1/payments")
    public ApiResponse<PaymentV1Dto.PaymentResponse> pay(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @Valid @RequestBody PaymentV1Dto.CreateRequest request
    ) {
        try {
            PaymentDto.PaymentInfo info = paymentFacade.pay(loginId, password, request.toCommand());
            return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    /** PG → 내부 시스템으로의 결제 결과 콜백 */
    @PostMapping("/api/v1/payments/callback")
    public ApiResponse<PaymentV1Dto.PaymentResponse> callback(
        @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        PaymentDto.PaymentInfo info = paymentFacade.handleCallback(request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    /** 관리자: 콜백 미수신 등으로 PENDING에 머문 결제건을 PG 조회 후 강제 동기화 */
    @PostMapping("/api-admin/v1/payments/{orderId}/sync")
    public ApiResponse<PaymentV1Dto.PaymentResponse> syncPayment(
        @PathVariable Long orderId
    ) {
        PaymentDto.PaymentInfo info = paymentFacade.syncPayment(orderId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}
