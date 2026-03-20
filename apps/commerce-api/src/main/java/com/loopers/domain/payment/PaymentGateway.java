package com.loopers.domain.payment;

import com.loopers.support.enums.CardType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 결제 게이트웨이 인터페이스.
 * <p>
 * DIP(의존성 역전 원칙)에 따라 도메인 계층에서 정의하며,
 * infrastructure 계층의 {@code ResilientPgClient}가 구현한다.
 * </p>
 * <p>
 * domain은 "결제를 요청할 수 있다"는 계약만 알고,
 * HTTP/RestTemplate/PG URL 등 기술 세부사항은 모른다.
 * </p>
 */
public interface PaymentGateway {

    /**
     * PG에 결제를 요청한다.
     *
     * @param orderId     주문 ID
     * @param userId      사용자 ID
     * @param cardType    카드 종류
     * @param cardNo      카드 번호
     * @param amount      결제 금액
     * @param callbackUrl 결제 결과 콜백 URL
     * @return PG 결제 요청 결과
     */
    GatewayPaymentResult requestPayment(Long orderId, Long userId,
                                         CardType cardType, String cardNo,
                                         BigDecimal amount, String callbackUrl);

    /**
     * PG에 결제 상태를 조회한다.
     *
     * @param transactionKey PG 트랜잭션 식별자
     * @return PG 결제 상태 조회 결과
     */
    GatewayPaymentResult getPaymentStatus(String transactionKey);

    /**
     * orderId로 PG에 결제 목록을 조회한다.
     * <p>
     * transactionKey 없이도 PG에서 결제 상태를 확인할 수 있어,
     * PG 응답 유실(타임아웃 등)로 transactionKey를 모르는 고아 Payment를 복구할 수 있다.
     * </p>
     *
     * @param orderId 주문 ID
     * @return 해당 주문의 PG 결제 결과 목록 (PG에 결제 없으면 빈 리스트)
     */
    List<GatewayPaymentResult> getPaymentsByOrderId(Long orderId);
}
