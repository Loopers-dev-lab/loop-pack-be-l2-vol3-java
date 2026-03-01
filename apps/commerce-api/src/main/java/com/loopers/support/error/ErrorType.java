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

    // User 도메인
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 가입된 ID입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "기존 비밀번호가 일치하지 않습니다."),

    // Brand 도메인
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),
    DUPLICATE_BRAND_NAME(HttpStatus.CONFLICT, "DUPLICATE_BRAND_NAME", "이미 등록된 브랜드명입니다."),
    BRAND_NOT_DELETED(HttpStatus.BAD_REQUEST, "BRAND_NOT_DELETED", "삭제되지 않은 브랜드입니다."),

    // Product 도메인
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),
    DUPLICATE_PRODUCT_NAME(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_NAME", "이미 존재하는 상품명입니다."),
    PRODUCT_NOT_DELETED(HttpStatus.BAD_REQUEST, "PRODUCT_NOT_DELETED", "삭제되지 않은 상품입니다."),
    PRODUCT_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "PRODUCT_INSUFFICIENT_STOCK", "재고가 부족합니다."),

    // Order 도메인
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "ORDER_EMPTY_ITEMS", "주문 항목은 1개 이상이어야 합니다."),
    ORDER_INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "ORDER_INVALID_STATUS_TRANSITION", "주문 상태를 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
