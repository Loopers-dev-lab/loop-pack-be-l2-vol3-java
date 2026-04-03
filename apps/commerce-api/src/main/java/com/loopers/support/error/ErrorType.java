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
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "만료된 쿠폰입니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "이미 발급된 쿠폰입니다."),
    COUPON_UNAVAILABLE(HttpStatus.BAD_REQUEST, "COUPON_UNAVAILABLE", "사용할 수 없는 쿠폰입니다."),
    COUPON_ALREADY_USED(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
    COUPON_NOT_OWNED(HttpStatus.FORBIDDEN, "COUPON_NOT_OWNED", "본인 소유의 쿠폰이 아닙니다."),
    COUPON_MIN_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "COUPON_MIN_ORDER_AMOUNT", "최소 주문 금액을 충족하지 않습니다."),
    PAYMENT_CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_CIRCUIT_OPEN", "현재 결제 시스템이 원활하지 않습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_TIMEOUT", "결제 요청이 시간 초과되었습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "PAYMENT_FAILED", "결제가 실패하였습니다."),

    // 대기열 에러
    // 토큰이 없거나 TTL이 만료된 경우 — 재진입 유도
    QUEUE_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "QUEUE_TOKEN_NOT_FOUND", "대기열 토큰이 유효하지 않습니다. 다시 대기열에 진입해주세요."),
    // 입장 허가 없이 서비스 API를 직접 호출한 경우 — Gate 2 역할
    QUEUE_NOT_ENTERED(HttpStatus.FORBIDDEN, "QUEUE_NOT_ENTERED", "입장 허가가 없습니다. 대기열을 통해 입장해주세요."),
    // Redis 장애 시 대기열 서비스 불가 — Graceful Degradation (fail closed: DB 보호 우선)
    QUEUE_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "QUEUE_SERVICE_UNAVAILABLE", "대기열 서비스가 일시적으로 이용 불가합니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
