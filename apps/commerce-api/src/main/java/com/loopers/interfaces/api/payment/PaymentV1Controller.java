package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller implements PaymentV1ApiSpec {

    private final PaymentFacade paymentFacade;

    // 결제 요청 — 사용자가 호출, PG에 비동기 결제 요청 후 PENDING 응답
    @PostMapping
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> requestPayment(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @RequestBody PaymentV1Dto.PaymentRequest request
    ) {
        PaymentInfo info = paymentFacade.requestPayment(loginId, loginPw, request);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }

    // 콜백 수신 — PG가 호출, 인증 헤더 없음
    // PG가 결제 처리(1~5초) 완료 후 결과를 이 URL로 POST
    @PostMapping("/callback")
    @Override
    public ApiResponse<Object> handleCallback(
            @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        paymentFacade.handleCallback(request);
        return ApiResponse.success();
    }

    // 결제 조회 + 수동 복구 — 콜백이 안 왔을 때 직접 PG에 상태 확인
    @GetMapping("/{paymentId}")
    @Override
    public ApiResponse<PaymentV1Dto.PaymentResponse> getPayment(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @PathVariable Long paymentId
    ) {
        PaymentInfo info = paymentFacade.syncPaymentStatus(loginId, loginPw, paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponse.from(info));
    }
}
