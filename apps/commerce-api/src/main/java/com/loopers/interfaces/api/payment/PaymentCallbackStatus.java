package com.loopers.interfaces.api.payment;

/**
 * PG 콜백에서 전달되는 결제 상태값.
 * 허용되지 않는 상태값은 Jackson 역직렬화 단계에서 거부된다.
 */
public enum PaymentCallbackStatus {
    SUCCESS,
    FAILED,
    PENDING,
    PROCESSING
}
