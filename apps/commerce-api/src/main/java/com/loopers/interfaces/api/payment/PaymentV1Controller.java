package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping("/api/v1/payments")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @LoginMember MemberModel member,
        @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(member.getId(), request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/api/v1/payments/{paymentId}")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> getPayment(
        @LoginMember MemberModel member,
        @PathVariable Long paymentId
    ) {
        PaymentInfo info = paymentFacade.getPayment(paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/api/v1/payments")
    @Override
    public ApiResponse<List<PaymentV1Dto.PaymentResponse>> getPaymentsByOrderId(
        @LoginMember MemberModel member,
        @RequestParam Long orderId
    ) {
        List<PaymentInfo> infos = paymentFacade.getPaymentsByOrderId(orderId);
        List<PaymentV1Dto.PaymentResponse> responses = infos.stream()
            .map(PaymentV1Dto.PaymentResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @PostMapping("/api/v1/payments/{paymentId}/sync")
    public ApiResponse<PaymentV1Dto.PaymentResponse> syncPaymentStatus(
        @LoginMember MemberModel member,
        @PathVariable Long paymentId
    ) {
        PaymentInfo info = paymentFacade.syncPaymentStatus(paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/api/v1/payments/callback")
    @Override
    public ApiResponse<Object> handleCallback(
        @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        paymentFacade.handleCallback(request.transactionKey(), request.status(), request.failureReason());
        return ApiResponse.success();
    }
}
