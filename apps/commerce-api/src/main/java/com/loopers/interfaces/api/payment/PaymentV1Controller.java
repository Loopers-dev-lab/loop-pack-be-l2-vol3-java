package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentV1Controller implements PaymentApiV1Spec {

    private final PaymentFacade paymentFacade;

    // Command

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @AuthUser AuthenticatedUser user,
            @RequestBody @Valid PaymentRequest.Request request) {
        PaymentInfo info = paymentFacade.requestPayment(user.id(), request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/{paymentId}/cancel")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> cancelPayment(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long paymentId,
            @RequestBody @Valid PaymentRequest.Cancel request) {
        PaymentInfo info = paymentFacade.cancelPayment(user.id(), paymentId, request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/{paymentId}/verify")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> verifyPayment(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long paymentId) {
        PaymentInfo info = paymentFacade.verifyPayment(user.id(), paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    // Query

    @GetMapping("/{paymentId}")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> getPaymentDetail(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long paymentId) {
        PaymentInfo info = paymentFacade.getPaymentDetail(user.id(), paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}
