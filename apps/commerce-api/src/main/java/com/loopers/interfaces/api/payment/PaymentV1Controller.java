package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.application.payment.PgPaymentStatus;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
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

    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> pay(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @Valid @RequestBody PaymentV1Dto.PayRequest request
    ) {
        PaymentInfo info = paymentFacade.pay(loginId, password, request.orderId(), request.cardType(), request.cardNo());
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> getMyPayment(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @PathVariable Long orderId
    ) {
        PaymentInfo info = paymentFacade.getMyPayment(loginId, password, orderId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/{orderId}/sync")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> syncMyPayment(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @PathVariable Long orderId
    ) {
        PaymentInfo info = paymentFacade.syncMyPayment(loginId, password, orderId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    @PostMapping("/callback")
    @Override
    public ApiResponse<Object> callback(@RequestBody PaymentV1Dto.CallbackRequest request) {
        PgPaymentStatus status = request.status() != null ? request.status() : PgPaymentStatus.UNKNOWN;
        paymentFacade.handleCallback(request.orderId(), request.resolvedPaymentKey(), status, request.resolvedReason());
        return ApiResponse.success();
    }
}
