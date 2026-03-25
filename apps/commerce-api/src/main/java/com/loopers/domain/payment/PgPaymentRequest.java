package com.loopers.domain.payment;

/**
 * PG 결제 요청 DTO.
 * 우리 서버 → PG 서버로 전달하는 데이터.
 *
 * PG 시뮬레이터 스펙에 맞춤:
 * - orderId: String (6자리 이상)
 * - amount: Long
 * - cardType: String (SAMSUNG, KB, HYUNDAI)
 * - cardNo: String (xxxx-xxxx-xxxx-xxxx 형식)
 * - callbackUrl: String (콜백 수신 URL)
 */
public record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {
}
