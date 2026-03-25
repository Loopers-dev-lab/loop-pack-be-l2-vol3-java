package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentResultHandler;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;
    private final PaymentResultHandler resultHandler;

    // 결제 요청
    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @LoginUser UserInfo loginUser,
            @RequestBody PaymentV1Dto.PaymentRequest request)
    {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(
                paymentFacade.requestPayment(loginUser.id(), request.toCommand())));
    }

    // PG 콜백 수신 (PG 시스템이 호출, 인증 없음)
    @PostMapping("/callback")
    public ApiResponse<Object> handleCallback(
            @RequestBody PaymentV1Dto.PaymentCallbackRequest request)
    {
        resultHandler.handleCallback(request.transactionKey(),request.status(),request.reason());
        return ApiResponse.success();
    }

    // 결제 상태 조회 (프론트엔드 폴링용)
    @GetMapping
    public ApiResponse<PaymentV1Dto.PaymentResponse> getPaymentStatus(
            @LoginUser UserInfo loginUser,
            @RequestParam Long orderId)
    {
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(
                paymentFacade.findByOrderId(orderId, loginUser.id())));
    }
}
