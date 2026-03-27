package com.loopers.infrastructure.pg.exception;

import lombok.Getter;

@Getter
public class PgClientException extends RuntimeException {
    private final int httpStatusCode;
    private final String errorCode;
    private final boolean retryable;

    private PgClientException(String message, int httpStatusCode, String errorCode, boolean retryable) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    private PgClientException(String message, Throwable cause, int httpStatusCode, String errorCode, boolean retryable) {
        super(message, cause);
        this.httpStatusCode = httpStatusCode;
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public static PgClientException clientError(int statusCode, String message) {
        return new PgClientException(message, statusCode, "PG_CLIENT_ERROR", false);
    }

    public static PgClientException serverError(int statusCode, String message) {
        return new PgClientException(message, statusCode, "PG_SERVER_ERROR", true);
    }

    public static PgClientException connectionError(Throwable cause) {
        return new PgClientException("PG 서버 연결 실패", cause, 0, "PG_CONNECTION_ERROR", true);
    }

    public static PgClientException parseError(Throwable cause) {
        return new PgClientException("PG 응답 파싱 실패", cause, 0, "PG_PARSE_ERROR", false);
    }
}
