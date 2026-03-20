package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
        @Valid @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
            request.orderId(), request.cardType(), request.cardNo(), request.amount()
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(result));
    }

    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentV1Dto.PaymentDetailResponse> getPayment(
        @PathVariable Long paymentId
    ) {
        PaymentModel payment = paymentFacade.getPayment(paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentDetailResponse.from(payment));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentV1Dto.PaymentDetailResponse> getPaymentByOrderId(
        @PathVariable Long orderId
    ) {
        PaymentModel payment = paymentFacade.getPaymentByOrderId(orderId);
        return ApiResponse.success(PaymentV1Dto.PaymentDetailResponse.from(payment));
    }
}
