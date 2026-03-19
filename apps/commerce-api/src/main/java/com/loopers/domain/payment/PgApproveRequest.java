package com.loopers.domain.payment;

/**
 * PG 결제 승인 요청 DTO (Domain Layer)
 * Infrastructure 기술에 의존하지 않는 순수 데이터 객체
 */
public record PgApproveRequest(
        Long userId,
        String orderId,
        String cardType,
        String cardNo,
        int amount,
        String callbackUrl
) {}
