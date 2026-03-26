package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;
import com.loopers.support.enums.CardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 결제 API V1 DTO 래퍼 클래스.
 * <p>
 * 결제 요청, PG 콜백 수신, 결제 응답에 사용되는 DTO를 내부 static record로 정의한다.
 * </p>
 */
public class PaymentV1Dto {

    /**
     * 결제 요청 DTO.
     *
     * @param orderId  주문 ID (필수)
     * @param cardType 카드 종류 (필수)
     * @param cardNo   카드 번호 (xxxx-xxxx-xxxx-xxxx 형식, 필수)
     */
    public record PaymentRequest(
            @NotNull Long orderId,
            @NotNull CardType cardType,
            @NotBlank String cardNo
    ) {}

    /**
     * PG 콜백 수신 DTO.
     *
     * @param transactionKey PG 트랜잭션 식별자
     * @param status         결제 결과 ("SUCCESS" / "FAILED")
     * @param reason         실패 사유 (성공 시 null)
     */
    public record CallbackRequest(
            String transactionKey,
            String status,
            String reason
    ) {}

    /**
     * 결제 응답 DTO.
     *
     * @param paymentId      결제 ID
     * @param orderId        주문 ID
     * @param transactionKey PG 트랜잭션 식별자
     * @param status         결제 상태
     * @param cardNoMasked   마스킹된 카드 번호
     * @param amount         결제 금액
     */
    public record PaymentResponse(
            Long paymentId,
            Long orderId,
            String transactionKey,
            String status,
            String cardNoMasked,
            BigDecimal amount
    ) {
        /**
         * PaymentInfo에서 응답 DTO로 변환한다.
         *
         * @param info 결제 정보
         * @return PaymentResponse
         */
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                    info.paymentId(), info.orderId(), info.transactionKey(),
                    info.status(), info.cardNoMasked(), info.amount()
            );
        }
    }
}
