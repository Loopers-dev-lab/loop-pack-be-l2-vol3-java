package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/callback")
@RequiredArgsConstructor
public class PaymentCallbackV1Controller {

    private final PaymentFacade paymentFacade;

    @PostMapping
    public ApiResponse<Object> handleCallback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(
                request.orderId(), request.transactionKey(), request.status(), request.reason()
        );
        return ApiResponse.success();
    }
}
