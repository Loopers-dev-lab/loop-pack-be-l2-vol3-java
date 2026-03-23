package com.loopers.infrastructure.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 요청이 PG 서버에 요청하지 못한 경우 발생하는 예외 (retry 가능)
 * ex) PG 서버 다운, 포트 문제, DNS 문제
 */
public class PgConnectionException extends CoreException {

    public PgConnectionException() {
        super(ErrorType.INTERNAL_ERROR, "PG 서버에 연결할 수 없습니다.");
    }
}
