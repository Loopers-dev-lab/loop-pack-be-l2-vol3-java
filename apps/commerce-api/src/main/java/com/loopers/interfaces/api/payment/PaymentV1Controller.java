package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentApplicationService;
import com.loopers.domain.payment.Payment;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentApplicationService paymentApplicationService;

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @AuthUser AuthenticatedUser authUser,
        @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        Payment payment = paymentApplicationService.requestPayment(
            authUser.userId(), request.orderId(), request.cardType(), request.cardNo()
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(payment));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Void> handleCallback(
        @Valid @RequestBody PaymentV1Dto.PaymentCallbackRequest request
    ) {
        paymentApplicationService.handleCallback(
            request.transactionKey(), request.status(), request.reason()
        );
        return ApiResponse.success();
    }

    @PostMapping("/{transactionKey}/sync")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> syncPaymentStatus(
        @AuthUser AuthenticatedUser authUser,
        @PathVariable String transactionKey
    ) {
        Payment payment = paymentApplicationService.syncPaymentStatus(
            authUser.userId(), transactionKey
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(payment));
    }

    @PostMapping("/orders/{orderId}/sync")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> syncByOrderId(
        @AuthUser AuthenticatedUser authUser,
        @PathVariable Long orderId
    ) {
        Payment payment = paymentApplicationService.syncByOrderId(
            authUser.userId(), orderId
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(payment));
    }
}
