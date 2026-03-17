package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {
    private final PaymentFacade paymentFacade;

    @PostMapping("/callback")
    public ApiResponse<Void> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(request.toCommand());
        return ApiResponse.success(null);
    }
}
