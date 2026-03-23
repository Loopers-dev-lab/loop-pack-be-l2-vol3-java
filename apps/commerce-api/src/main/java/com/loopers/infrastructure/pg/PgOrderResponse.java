package com.loopers.infrastructure.pg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** PG 주문별 트랜잭션 조회(GET ?orderId=) 응답 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PgOrderResponse(
    String orderId,
    List<PgPaymentInfo> transactions
) {
}
