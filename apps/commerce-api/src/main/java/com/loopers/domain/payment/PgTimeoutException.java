package com.loopers.domain.payment;

/**
 * PG 타임아웃/연결 실패 — CB record 대상
 *
 * approve() 타임아웃 후 query()도 실패한 경우, 또는 query() 자체 타임아웃.
 * "PG가 진짜 죽었는가"를 판단하는 근거가 된다.
 */
public class PgTimeoutException extends PgException {

    public PgTimeoutException(String message) {
        super(message);
    }

    public PgTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
