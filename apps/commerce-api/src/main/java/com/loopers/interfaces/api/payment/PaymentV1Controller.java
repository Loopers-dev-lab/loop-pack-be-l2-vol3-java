package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentResult;
import com.loopers.application.payment.PaymentService;
import com.loopers.application.payment.PaymentSyncResult;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentV1Controller {

    private final PaymentService paymentService;

    /**
     * 결제 요청.
     */
    @PostMapping
    public ApiResponse<PaymentV1Dto.PaymentResponseDto> requestPayment(
            @UserId Long userId,
            @RequestBody PaymentV1Dto.PaymentRequestDto request) {
        PaymentResult result = paymentService.requestPayment(
                userId,
                request.orderId(),
                request.cardType(),
                request.cardNo()
        );
        return ApiResponse.success(PaymentV1Dto.PaymentResponseDto.from(result));
    }

    /**
     * PG 콜백 수신.
     */
    @PostMapping("/callback")
    public ApiResponse<PaymentV1Dto.PaymentResponseDto> handleCallback(
            @RequestBody PaymentV1Dto.PaymentCallbackDto callback) {
        PgPaymentResponse pgResponse = new PgPaymentResponse(
                callback.transactionId(),
                callback.orderId(),
                PgPaymentStatus.valueOf(callback.status()),
                callback.amount(),
                callback.cardType(),
                callback.cardNo(),
                callback.createdAt(),
                callback.updatedAt(),
                callback.failureReason()
        );
        PaymentResult result = paymentService.handleCallback(pgResponse);
        return ApiResponse.success(PaymentV1Dto.PaymentResponseDto.from(result));
    }

    /**
     * 결제 조회.
     */
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentV1Dto.PaymentResponseDto> getPayment(
            @PathVariable Long paymentId) {
        PaymentResult result = paymentService.getPayment(paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponseDto.from(result));
    }

    /**
     * 주문으로 결제 조회.
     */
    @GetMapping("/orders/{orderId}")
    public ApiResponse<PaymentV1Dto.PaymentResponseDto> getPaymentByOrderId(
            @PathVariable Long orderId) {
        PaymentResult result = paymentService.getPaymentByOrderId(orderId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponseDto.from(result));
    }

    /**
     * 단건 결제 상태 동기화.
     * PG에 결제 상태를 조회하여 로컬 DB와 동기화.
     */
    @PostMapping("/{paymentId}/sync")
    public ApiResponse<PaymentV1Dto.PaymentResponseDto> syncPaymentStatus(
            @UserId Long userId,
            @PathVariable Long paymentId) {
        PaymentResult result = paymentService.syncPaymentStatus(userId, paymentId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponseDto.from(result));
    }

    /**
     * 주문으로 결제 상태 동기화.
     */
    @PostMapping("/orders/{orderId}/sync")
    public ApiResponse<PaymentV1Dto.PaymentResponseDto> syncPaymentStatusByOrderId(
            @UserId Long userId,
            @PathVariable Long orderId) {
        PaymentResult result = paymentService.syncPaymentStatusByOrderId(userId, orderId);
        return ApiResponse.success(PaymentV1Dto.PaymentResponseDto.from(result));
    }

    /**
     * 모든 PENDING 상태 결제 동기화 (관리자용).
     */
    @PostMapping("/sync-all")
    public ApiResponse<PaymentV1Dto.PaymentSyncResponseDto> syncAllPendingPayments() {
        PaymentSyncResult result = paymentService.syncAllPendingPayments();
        return ApiResponse.success(PaymentV1Dto.PaymentSyncResponseDto.from(result));
    }
}
