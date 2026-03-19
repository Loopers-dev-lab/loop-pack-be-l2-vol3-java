package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<PaymentV1Dto.Response> requestPayment(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo paymentInfo = paymentFacade.requestPayment(
                request.orderId(), userId, request.cardType(), request.cardNo()
        );
        return ApiResponse.success(PaymentV1Dto.Response.from(paymentInfo));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentV1Dto.Response> getPayment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long orderId
    ) {
        PaymentInfo paymentInfo = paymentFacade.getPayment(orderId, userId);
        return ApiResponse.success(PaymentV1Dto.Response.from(paymentInfo));
    }

    @PostMapping("/recover")
    public ApiResponse<Object> recoverAllPendingPayments() {
        paymentFacade.recoverPendingPayments();
        return ApiResponse.success();
    }

    @PostMapping("/orders/{orderId}/recover")
    public ApiResponse<Object> recoverPaymentByOrderId(@PathVariable Long orderId) {
        paymentFacade.recoverPaymentByOrderId(orderId);
        return ApiResponse.success();
    }
}
