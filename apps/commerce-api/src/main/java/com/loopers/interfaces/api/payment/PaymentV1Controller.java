package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentService paymentService;

    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(@Valid @RequestBody PaymentV1Dto.PaymentRequest request) {
        PaymentService.PaymentResult result = paymentService.requestPayment(
            request.orderId(),
            request.memberId(),
            request.amount(),
            request.cardType(),
            request.cardNo()
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(result));
    }

    @PostMapping("/callback")
    public ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentService.handleCallback(request.transactionKey(), request.status(), request.reason());
        return ApiResponse.success();
    }
}
