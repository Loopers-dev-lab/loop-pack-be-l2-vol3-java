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
    ADMIN_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "관리자 권한이 필요합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
