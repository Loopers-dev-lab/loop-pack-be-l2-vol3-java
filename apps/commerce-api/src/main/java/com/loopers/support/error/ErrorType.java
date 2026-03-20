package com.loopers.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
    PAYMENT_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CIRCUIT_OPEN", "현재 결제 시스템이 원활하지 않습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_TIMEOUT", "결제 요청이 시간 초과되었습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED", "결제가 실패하였습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
