package com.loopers.support.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorType {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하는 리소스입니다."),
    
    /** 회원 관련 에러 (requirements/featured.md 섹션 5) */
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 로그인 ID입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호 규칙을 확인해주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증에 실패했습니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "현재 비밀번호가 일치하지 않습니다."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "SAME_PASSWORD", "새 비밀번호는 현재 비밀번호와 달라야 합니다."),
    
    /** Validation 에러 */
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값이 올바르지 않습니다."),

    /** User */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    DUPLICATE_USER_ID(HttpStatus.CONFLICT, "DUPLICATE_USER_ID", "이미 사용 중인 사용자 ID입니다."),

    /** Brand */
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),
    DUPLICATE_BRAND(HttpStatus.CONFLICT, "DUPLICATE_BRAND", "이미 존재하는 브랜드입니다."),

    /** Product */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_ORDERABLE(HttpStatus.CONFLICT, "PRODUCT_NOT_ORDERABLE", "주문할 수 없는 상품입니다."),
    INVALID_STOCK_UPDATE(HttpStatus.BAD_REQUEST, "INVALID_STOCK_UPDATE", "재고 수정이 유효하지 않습니다."),

    /** Stock */
    STOCK_NOT_ENOUGH(HttpStatus.CONFLICT, "STOCK_NOT_ENOUGH", "재고가 부족합니다."),

    /** Like */
    LIKE_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE_PRODUCT_NOT_FOUND", "좋아요 대상 상품을 찾을 수 없습니다."),

    /** Cart */
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다."),
    CART_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "CART_LIMIT_EXCEEDED", "장바구니 최대 수량을 초과했습니다."),
    CART_STOCK_EXCEEDED(HttpStatus.BAD_REQUEST, "CART_STOCK_EXCEEDED", "구매 가능 재고를 초과했습니다."),

    /** Order */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_NOT_CANCELLABLE(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "취소할 수 없는 주문입니다."),
    ORDER_NOT_CREATABLE(HttpStatus.CONFLICT, "ORDER_NOT_CREATABLE", "주문을 생성할 수 없습니다."),
    ORDER_ITEM_EMPTY(HttpStatus.BAD_REQUEST, "ORDER_ITEM_EMPTY", "주문 항목이 비어 있습니다."),
    ORDER_PENDING_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "ORDER_PENDING_LIMIT_EXCEEDED", "동시 결제 대기 주문은 최대 10건까지 가능합니다."),

    /** Admin */
    ADMIN_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "관리자 인증에 실패했습니다."),

    /** Coupon */
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다."),
    COUPON_ALREADY_ISSUED(HttpStatus.CONFLICT, "COUPON_ALREADY_ISSUED", "이미 발급된 쿠폰입니다."),
    COUPON_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "COUPON_NOT_APPLICABLE", "적용할 수 없는 쿠폰입니다."),
    COUPON_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "COUPON_NOT_AVAILABLE", "사용 불가능한 쿠폰입니다."),
    USER_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_COUPON_NOT_FOUND", "발급된 쿠폰을 찾을 수 없습니다."),

    /** Payment */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
    PAYMENT_NOT_PAYABLE(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_PAYABLE", "결제할 수 없는 주문 상태입니다."),
    PAYMENT_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "PAYMENT_ALREADY_IN_PROGRESS", "이미 결제가 진행 중입니다."),
    PAYMENT_STATUS_INVALID(HttpStatus.BAD_REQUEST, "PAYMENT_STATUS_INVALID", "유효하지 않은 결제 상태 전이입니다."),
    PAYMENT_PG_ERROR(HttpStatus.BAD_GATEWAY, "PAYMENT_PG_ERROR", "결제 시스템에 문제가 발생했습니다."),
    PAYMENT_PG_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PAYMENT_PG_TIMEOUT", "결제 확인 중입니다."),
    PAYMENT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_SERVICE_UNAVAILABLE", "결제 서비스를 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
