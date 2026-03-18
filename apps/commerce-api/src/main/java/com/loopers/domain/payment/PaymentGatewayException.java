package com.loopers.domain.payment;

/**
 * PG 연동 중 발생하는 예외.
 * 타임아웃, 서버 에러 등 외부 시스템 장애를 나타낸다.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
