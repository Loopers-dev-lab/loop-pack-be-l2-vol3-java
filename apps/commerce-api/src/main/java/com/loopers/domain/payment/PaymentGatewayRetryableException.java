package com.loopers.domain.payment;

/**
 * PG 연동 중 발생하는 일시적 장애 예외.
 * 타임아웃, 연결 실패, 서버 5xx 등 재시도로 해결될 수 있는 장애를 나타낸다.
 * Resilience4j Retry의 재시도 대상 예외로 사용된다.
 */
public class PaymentGatewayRetryableException extends PaymentGatewayException {

    public PaymentGatewayRetryableException(String message) {
        super(message);
    }

    public PaymentGatewayRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
