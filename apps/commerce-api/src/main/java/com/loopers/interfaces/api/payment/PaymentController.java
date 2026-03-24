package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.CurrentUser;
import com.loopers.interfaces.auth.LoginRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class PaymentController {

    private final PaymentFacade paymentFacade;

    @LoginRequired
    @PostMapping("/api/v1/payments")
    public ApiResponse<Void> pay(@CurrentUser AuthenticatedUser user,
                                 @RequestBody PaymentDto.PayRequest request) {

        paymentFacade.processPayment(request.orderId(), request.cardType(), request.cardNo(), user.id());
        return ApiResponse.success(null);
    }

    @PostMapping("/api/v1/payments/callback")
    public ApiResponse<Void> callback(@RequestBody PaymentDto.CallbackRequest request) {
        paymentFacade.handleCallback(request.transactionKey(), request.orderId(), request.status(), request.reason());
        return ApiResponse.success(null);
    }

    @LoginRequired
    @GetMapping("/api/v1/payments/{orderId}")
    public ApiResponse<PaymentDto.PaymentResponse> getPayment(@CurrentUser AuthenticatedUser user,
                                                              @PathVariable String orderId) {
        PaymentInfo paymentInfo = paymentFacade.getPayment(orderId, user.id());
        return ApiResponse.success(new PaymentDto.PaymentResponse(
                paymentInfo.orderId(),
                paymentInfo.status().name(),
                paymentInfo.reason()));
    }
}
