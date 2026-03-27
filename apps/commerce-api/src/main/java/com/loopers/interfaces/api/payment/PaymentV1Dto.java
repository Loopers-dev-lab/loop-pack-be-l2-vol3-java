package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentResult;
import com.loopers.application.payment.PaymentSyncResult;
import com.loopers.domain.payment.PaymentStatus;

import java.time.ZonedDateTime;

/**
 * 결제 API DTO.
 */
public class PaymentV1Dto {

    private PaymentV1Dto() {}

    /**
     * 결제 요청 DTO.
     */
    public record PaymentRequestDto(
            Long orderId,
            String cardType,
            String cardNo
    ) {}

    /**
     * 결제 응답 DTO.
     */
    public record PaymentResponseDto(
            Long paymentId,
            Long orderId,
            String transactionId,
            Long amount,
            PaymentStatus status,
            String failReason,
            String errorMessage,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static PaymentResponseDto from(PaymentResult result) {
            return new PaymentResponseDto(
                    result.paymentId(),
                    result.orderId(),
                    result.transactionId(),
                    result.amount(),
                    result.status(),
                    result.failReason(),
                    result.errorMessage(),
                    result.createdAt(),
                    result.updatedAt()
            );
        }

        public boolean hasError() {
            return errorMessage != null;
        }
    }

    /**
     * PG 콜백 DTO (PG에서 보내는 형식).
     */
    public record PaymentCallbackDto(
            String transactionId,
            String orderId,
            String status,
            Long amount,
            String cardType,
            String cardNo,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt,
            String failureReason
    ) {}

    /**
     * 결제 동기화 결과 응답 DTO.
     */
    public record PaymentSyncResponseDto(
            int totalCount,
            int successCount,
            int failCount,
            int skipCount
    ) {
        public static PaymentSyncResponseDto from(PaymentSyncResult result) {
            return new PaymentSyncResponseDto(
                    result.totalCount(),
                    result.successCount(),
                    result.failCount(),
                    result.skipCount()
            );
        }
    }
}
