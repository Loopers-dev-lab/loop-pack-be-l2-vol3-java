package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<PaymentV1Dto.PayResponse> pay(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestBody PaymentV1Dto.PayRequest request
    ) {
        PaymentInfo info = paymentFacade.pay(loginId, password, request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Dto.PayResponse.from(info));
    }

    @PostMapping("/callback")
    @ResponseStatus(HttpStatus.OK)
    public void callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        paymentFacade.handleCallback(Long.parseLong(request.orderId()), request.transactionId(), request.success());
    }
}