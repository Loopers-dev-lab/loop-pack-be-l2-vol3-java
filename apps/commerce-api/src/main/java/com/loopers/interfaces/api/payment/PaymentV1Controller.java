package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.config.CommerceApiProperties;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;
    private final CommerceApiProperties commerceApiProperties;

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<PaymentResponse>> requestPayment(
            @Valid @RequestBody PaymentRequest request
    ) {
        String callbackUrl = commerceApiProperties.callbackBaseUrl() + "/api/v1/payments/callback";
        PaymentInfo info = paymentFacade.requestPayment(request.toCommand(), callbackUrl);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(PaymentResponse.from(info)));
    }
}
