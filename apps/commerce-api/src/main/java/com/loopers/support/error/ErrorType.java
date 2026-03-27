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

    /** 인증 관련 에러 */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "인증에 실패했습니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "존재하지 않는 사용자입니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다."),

    /** 브랜드 관련 에러 */
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "존재하지 않는 브랜드입니다."),
    BRAND_ALREADY_EXISTS(HttpStatus.CONFLICT, "BRAND_ALREADY_EXISTS", "이미 존재하는 브랜드명입니다."),
    BRAND_DELETED(HttpStatus.BAD_REQUEST, "BRAND_DELETED", "삭제된 브랜드입니다."),

    /** 상품 관련 에러 */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "존재하지 않는 상품입니다."),
    PRODUCT_DELETED(HttpStatus.BAD_REQUEST, "PRODUCT_DELETED", "삭제된 상품입니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "재고가 부족합니다."),

    /** 주문 관련 에러 */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "존재하지 않는 주문입니다."),
    ORDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "주문 접근 권한이 없습니다."),

    /** 관리자 관련 에러 */
    ADMIN_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "관리자 권한이 필요합니다."),

    /** 쿠폰 관련 에러 */
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "존재하지 않는 쿠폰입니다."),
    COUPON_EXHAUSTED(HttpStatus.BAD_REQUEST, "COUPON_EXHAUSTED", "쿠폰 발급이 마감되었습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다."),
    COUPON_EXPIRED(HttpStatus.BAD_REQUEST, "COUPON_EXPIRED", "만료된 쿠폰입니다."),
    COUPON_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "COUPON_NOT_AVAILABLE", "사용할 수 없는 쿠폰입니다."),
    COUPON_ALREADY_USED(HttpStatus.BAD_REQUEST, "COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
    COUPON_ACCESS_DENIED(HttpStatus.FORBIDDEN, "COUPON_ACCESS_DENIED", "쿠폰 접근 권한이 없습니다."),

    /** 주문 확장 에러 */
    ORDER_AMOUNT_TOO_LOW(HttpStatus.BAD_REQUEST, "ORDER_AMOUNT_TOO_LOW", "최소 주문 금액을 충족하지 못했습니다."),

    /** 포인트 관련 에러 */
    POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_NOT_FOUND", "포인트 정보를 찾을 수 없습니다."),
    INSUFFICIENT_POINT(HttpStatus.BAD_REQUEST, "INSUFFICIENT_POINT", "포인트가 부족합니다."),

    /** 결제 관련 에러 */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PAYMENT_ALREADY_EXISTS", "이미 결제가 존재합니다."),
    PAYMENT_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_REQUEST_FAILED", "결제 요청에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
