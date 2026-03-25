package com.loopers.domain.payment;

/**
 * PG 클라이언트 에러 (4xx) — CB ignore 대상
 *
 * 잔액부족, 카드사 거절 등 비즈니스 거절. PG는 정상 동작한 것이므로 CB에 집계하면 오진.
 * PaymentService에서 catch → PaymentResult.failed()로 변환된다.
 */
public class PgClientException extends PgException {

    public PgClientException(String message) {
        super(message);
    }

    public PgClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
