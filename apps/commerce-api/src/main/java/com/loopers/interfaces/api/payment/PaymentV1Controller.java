package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentFacade;
import com.loopers.application.payment.PaymentRecoveryService;
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
    private final PaymentRecoveryService paymentRecoveryService;

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

    /**
     * PG 콜백 수신 엔드포인트.
     *
     * <p>PG사가 결제 결과를 비동기로 전송한다.
     * 즉시 200 OK 응답 + 비동기 처리.</p>
     */
    @PostMapping("/callback")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Object> handleCallback(
        @Valid @RequestBody PaymentV1Dto.CallbackRequest request
    ) {
        paymentRecoveryService.processCallback(
            request.transactionKey(), request.status(), request.payload());
        return ApiResponse.success();
    }

    /**
     * 수동 복구 — PENDING/UNKNOWN 결제건의 PG 상태를 확인하여 확정.
     */
    @PostMapping("/{paymentId}/confirm")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<String> manualConfirm(@PathVariable Long paymentId) {
        String result = paymentRecoveryService.manualConfirm(paymentId);
        return ApiResponse.success(result);
    }
}
