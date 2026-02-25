package com.loopers.support.error;

import org.springframework.http.HttpStatus;

public enum CouponErrorType implements ErrorType {
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰 템플릿입니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰입니다."),
    INVALID_TEMPLATE(HttpStatus.BAD_REQUEST, "유효하지 않은 쿠폰 템플릿입니다."),
    ISSUE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "전체 발급 제한을 초과했습니다."),
    USER_ISSUE_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "유저별 발급 제한을 초과했습니다."),
    INVALID_COUPON_STATUS(HttpStatus.CONFLICT, "현재 쿠폰 상태에서는 수행할 수 없는 작업입니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "본인의 쿠폰이 아닙니다."),
    INVALID_TEMPLATE_NAME(HttpStatus.BAD_REQUEST, "쿠폰 템플릿명은 필수입니다.");

    private final HttpStatus status;
    private final String message;

    CouponErrorType(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus getStatus() {
        return this.status;
    }

    @Override
    public String getCode() {
        return name();
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
