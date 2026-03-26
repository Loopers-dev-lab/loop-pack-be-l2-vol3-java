package com.loopers.application.payment;

import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentModel;

import java.math.BigDecimal;

/**
 * 결제 정보 DTO.
 * <p>
 * PaymentFacade → Controller 전달용.
 * 도메인 모델(PaymentModel)과 PG 응답(GatewayPaymentResult)을 조합하여
 * interfaces 계층에 전달하기 위한 응답 객체이다.
 * </p>
 *
 * @param paymentId      결제 ID
 * @param orderId        주문 ID
 * @param transactionKey PG 트랜잭션 식별자
 * @param status         결제 상태 문자열
 * @param cardNoMasked   마스킹된 카드 번호
 * @param amount         결제 금액
 */
public record PaymentInfo(
        Long paymentId,
        Long orderId,
        String transactionKey,
        String status,
        String cardNoMasked,
        BigDecimal amount
) {

    /**
     * PaymentModel과 GatewayPaymentResult를 조합하여 PaymentInfo를 생성한다.
     * PG 결제 요청 직후 사용.
     *
     * @param payment  결제 엔티티
     * @param pgResult PG 응답 결과
     * @return PaymentInfo
     */
    public static PaymentInfo of(PaymentModel payment, GatewayPaymentResult pgResult) {
        return new PaymentInfo(
                payment.getPaymentId(),
                payment.getOrderId(),
                pgResult.transactionKey(),
                pgResult.status(),
                payment.getCardNoMasked(),
                payment.getAmount()
        );
    }

    /**
     * PaymentModel만으로 PaymentInfo를 생성한다.
     * 결제 조회, 콜백 처리 등에서 사용.
     *
     * @param payment 결제 엔티티
     * @return PaymentInfo
     */
    public static PaymentInfo from(PaymentModel payment) {
        return new PaymentInfo(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getTransactionKey(),
                payment.getStatus().name(),
                payment.getCardNoMasked(),
                payment.getAmount()
        );
    }
}
