package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {
    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @LoginUser Long userId,
            @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(userId, request.toCommand());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/callback")
    public ApiResponse<Void> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(request.toCommand());
        return ApiResponse.success(null);
    }

    @PostMapping("/{paymentId}/sync")
    public ApiResponse<PaymentV1Dto.PaymentResponse> sync(
            @LoginUser Long userId,
            @PathVariable Long paymentId
    ) {
        PaymentInfo info = paymentFacade.syncPayment(userId, paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}
