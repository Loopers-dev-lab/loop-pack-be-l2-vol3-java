package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentCommand;
import com.loopers.application.payment.PaymentInfo;

import java.time.ZonedDateTime;

public class PaymentV1Dto {

    /**
     * 결제 요청
     */
    public record PaymentRequest(
            Long orderId,
            String cardType,
            String cardNo
    ) {
        public PaymentCommand toCommand() {
            return new PaymentCommand(orderId, cardType, cardNo);
        }
    }

    /**
     * PG 콜백 요청 (PG 시뮬레이터가 전송하는 페이로드)
     *
     * PG 시뮬레이터 스펙:
     * { transactionKey, orderId, cardType, cardNo, amount, status, reason }
     */
    public record PaymentCallbackRequest(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,         // "SUCCESS" | "FAILED"
            String reason          // 실패 시 사유 (성공이면 null)
    ) {}

    /**
     * 결제 응답 (요청 결과 / 상태 조회 공용)
     */
    public record PaymentResponse(
            Long paymentId,
            Long orderId,
            String transactionKey,
            String status,
            int amount,
            String cardType,
            String cardNo,
            String failureReason,
            ZonedDateTime pgRespondedAt,
            ZonedDateTime createdAt
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                    info.paymentId(),
                    info.orderId(),
                    info.transactionKey(),
                    info.status(),
                    info.amount(),
                    info.cardType(),
                    info.cardNo(),
                    info.failureReason(),
                    info.pgRespondedAt(),
                    info.createdAt()
            );
        }
    }
}
