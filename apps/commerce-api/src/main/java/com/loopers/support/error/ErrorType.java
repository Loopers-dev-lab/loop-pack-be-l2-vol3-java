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
    ALREADY_LIKED(HttpStatus.BAD_REQUEST, "ALREADY_LIKED", "이미 좋아요한 상품입니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "재고가 부족합니다."),

    /** 쿠폰 관련 에러 */
    COUPON_ALREADY_USED(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "만료된 쿠폰입니다."),
    MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "MIN_ORDER_AMOUNT_NOT_MET", "최소 주문 금액 조건을 충족하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
