package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.payment.dto.CreatePaymentApiReqDto;
import com.loopers.interfaces.api.payment.dto.FindPaymentApiResDto;
import com.loopers.interfaces.api.payment.dto.PaymentCallbackApiReqDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final PaymentFacade paymentFacade;

    @Override
    @PostMapping
    public ApiResponse<FindPaymentApiResDto> createPayment(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                           @RequestHeader(HEADER_LOGIN_PW) String password,
                                                           @RequestBody CreatePaymentApiReqDto request) {

        return ApiResponse.success(FindPaymentApiResDto.from(paymentFacade.createPayment(loginId, password, request.toDto())));
    }

    @Override
    @PostMapping("/callback")
    public ApiResponse<Void> handleCallback(@RequestBody PaymentCallbackApiReqDto request) {
        paymentFacade.handleCallback(request.transactionKey(), request.status());
        return ApiResponse.successNoContent();
    }

    @Override
    @GetMapping("/{orderId}/status")
    public ApiResponse<FindPaymentApiResDto> checkPaymentStatus(@RequestHeader(HEADER_LOGIN_ID) String loginId,
                                                                @RequestHeader(HEADER_LOGIN_PW) String password,
                                                                @PathVariable("orderId") String orderId) {

        return ApiResponse.success(FindPaymentApiResDto.from(paymentFacade.checkPaymentStatus(loginId, password, orderId)));
    }
}
