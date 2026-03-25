package com.loopers.domain.payment;

/**
 * PG 서버 에러 (500) — CB record 대상
 *
 * PG가 내부 에러를 반환한 경우. 재시도해도 같은 에러 반복 가능성 높음.
 */
public class PgServerException extends PgException {

    public PgServerException(String message) {
        super(message);
    }

    public PgServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
