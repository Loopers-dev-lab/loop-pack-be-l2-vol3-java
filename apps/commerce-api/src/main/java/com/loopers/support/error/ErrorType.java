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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "유효하지 않은 인증 정보입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.getReasonPhrase(), "접근 권한이 없습니다."),

    /** 쿠폰 에러 */
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "만료된 쿠폰입니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_ISSUED", "이미 발급된 쿠폰입니다."),
    COUPON_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "COUPON_NOT_AVAILABLE", "사용 불가능한 쿠폰입니다."),
    COUPON_OWNER_MISMATCH(HttpStatus.BAD_REQUEST, "COUPON_OWNER_MISMATCH", "본인의 쿠폰이 아닙니다."),
    COUPON_MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_MIN_ORDER_AMOUNT_NOT_MET", "최소 주문 금액을 충족하지 못했습니다."),
    USER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_COUPON_NOT_FOUND", "존재하지 않는 사용자 쿠폰입니다."),
    COUPON_TYPE_IMMUTABLE(HttpStatus.BAD_REQUEST, "COUPON_TYPE_IMMUTABLE", "쿠폰 타입과 값은 변경할 수 없습니다."),

    /** 결제 에러 */
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "이미 결제된 주문입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
