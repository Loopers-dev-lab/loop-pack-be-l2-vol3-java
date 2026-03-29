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
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.getReasonPhrase(), "로그인을 해주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 존재하는 리소스입니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(), "일시적으로 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요."),

    /** 공용 도메인 에러 */
    REQUIRED_MONEY_AMOUNT(HttpStatus.BAD_REQUEST, "REQUIRED_MONEY_AMOUNT", "금액은 필수 값입니다."),
    INVALID_MONEY_AMOUNT(HttpStatus.BAD_REQUEST, "INVALID_MONEY_AMOUNT", "금액은 0 이상이어야 합니다."),

    /** User 도메인 에러 */
    REQUIRED_LOGIN_ID(HttpStatus.BAD_REQUEST, "REQUIRED_LOGIN_ID", "로그인 ID는 필수 값입니다."),
    REQUIRED_EMAIL(HttpStatus.BAD_REQUEST, "REQUIRED_EMAIL", "이메일은 필수 값입니다."),
    REQUIRED_PASSWORD(HttpStatus.BAD_REQUEST, "REQUIRED_PASSWORD", "비밀번호는 필수 값입니다."),
    REQUIRED_USER_NAME(HttpStatus.BAD_REQUEST, "REQUIRED_USER_NAME", "이름은 필수 값입니다."),
    REQUIRED_BIRTH_DATE(HttpStatus.BAD_REQUEST, "REQUIRED_BIRTH_DATE", "생년월일은 필수 값입니다."),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "이메일은 xx@yy.zz 형식이어야 합니다."),
    INVALID_LOGIN_ID_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_LOGIN_ID_FORMAT", "로그인 ID는 영문과 숫자만 허용됩니다."),
    INVALID_PASSWORD_LENGTH(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_LENGTH", "비밀번호는 8~16자여야 합니다."),
    INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_FORMAT", "비밀번호는 영문 대소문자, 숫자, 특수문자만 허용됩니다."),
    INVALID_BIRTH_DATE_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_BIRTH_DATE_FORMAT", "생년월일은 yyyy-MM-dd 형식이어야 합니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.BAD_REQUEST, "DUPLICATE_LOGIN_ID", "이미 가입된 로그인 ID입니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "기존 비밀번호가 일치하지 않습니다."),
    PASSWORD_REUSE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PASSWORD_REUSE_NOT_ALLOWED", "기존 비밀번호와 동일한 비밀번호로 수정할 수 없습니다."),
    BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "BIRTH_DATE_IN_PASSWORD_NOT_ALLOWED", "비밀번호에 생년월일을 포함할 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    /** Brand 도메인 에러 */
    REQUIRED_BRAND_ID(HttpStatus.BAD_REQUEST, "REQUIRED_BRAND_ID", "브랜드 ID는 필수 값입니다."),
    REQUIRED_BRAND_NAME(HttpStatus.BAD_REQUEST, "REQUIRED_BRAND_NAME", "브랜드 이름은 필수 값입니다."),
    REQUIRED_BRAND_LOGO_URL(HttpStatus.BAD_REQUEST, "REQUIRED_BRAND_LOGO_URL", "브랜드 로고 URL은 필수 값입니다."),
    INVALID_BRAND_NAME(HttpStatus.BAD_REQUEST, "INVALID_BRAND_NAME", "브랜드 이름은 2자 이상 50자 이하이어야 합니다."),
    ALREADY_EXISTS_BRAND_NAME(HttpStatus.BAD_REQUEST, "ALREADY_EXISTS_BRAND_NAME", "이미 존재하는 브랜드 이름입니다."),
    ALREADY_DELETED_BRAND(HttpStatus.BAD_REQUEST, "ALREADY_DELETED_BRAND", "이미 삭제된 브랜드입니다."),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),

    /** Product 도메인 에러 */
    REQUIRED_PRODUCT_NAME(HttpStatus.BAD_REQUEST, "REQUIRED_PRODUCT_NAME", "상품 이름은 필수 값입니다."),
    REQUIRED_PRODUCT_THUMBNAIL_URL(HttpStatus.BAD_REQUEST, "REQUIRED_PRODUCT_THUMBNAIL_URL", "상품 썸네일 URL은 필수 값입니다."),
    REQUIRED_PRODUCT_STOCK(HttpStatus.BAD_REQUEST, "REQUIRED_PRODUCT_STOCK", "상품 재고는 필수 값입니다."),
    INVALID_STOCK(HttpStatus.BAD_REQUEST, "INVALID_STOCK", "재고는 1 이상이어야 합니다."),
    INVALID_PRODUCT_NAME(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_NAME", "상품 이름은 2자 이상 100자 이하이어야 합니다."),
    INVALID_SORT_TYPE(HttpStatus.BAD_REQUEST, "INVALID_SORT_TYPE", "유효하지 않은 정렬 옵션입니다."),
    ALREADY_DELETED_PRODUCT(HttpStatus.BAD_REQUEST, "ALREADY_DELETED_PRODUCT", "이미 삭제된 상품입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),

    /** Order 도메인 에러 */
    REQUIRED_ORDER_ITEM(HttpStatus.BAD_REQUEST, "REQUIRED_ORDER_ITEM", "주문 항목은 1개 이상이어야 합니다."),
    INVALID_ORDER_ITEM_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_ORDER_ITEM_QUANTITY", "주문 항목의 수량은 1 이상이어야 합니다."),
    DUPLICATE_ORDER_PRODUCT(HttpStatus.BAD_REQUEST, "DUPLICATE_ORDER_PRODUCT", "같은 상품이 중복 포함되어 있습니다."),
    SOLD_OUT_PRODUCT(HttpStatus.BAD_REQUEST, "SOLD_OUT_PRODUCT", "매진된 상품입니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
    FORBIDDEN_ORDER_ACCESS(HttpStatus.FORBIDDEN, "FORBIDDEN_ORDER_ACCESS", "접근 권한이 없는 주문입니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_NOT_PAYABLE(HttpStatus.BAD_REQUEST, "ORDER_NOT_PAYABLE", "결제할 수 없는 주문 상태입니다."),
    ORDER_NOT_FAILABLE(HttpStatus.BAD_REQUEST, "ORDER_NOT_FAILABLE", "실패 처리할 수 없는 주문 상태입니다."),

    /** Like 도메인 에러 */
    REQUIRED_USER_ID(HttpStatus.BAD_REQUEST, "REQUIRED_USER_ID", "사용자 ID는 필수 값입니다."),
    REQUIRED_PRODUCT_ID(HttpStatus.BAD_REQUEST, "REQUIRED_PRODUCT_ID", "상품 ID는 필수 값입니다."),

    /** Coupon 도메인 에러 */
    REQUIRED_COUPON_NAME(HttpStatus.BAD_REQUEST, "REQUIRED_COUPON_NAME", "쿠폰명은 필수 값입니다."),
    INVALID_COUPON_NAME(HttpStatus.BAD_REQUEST, "INVALID_COUPON_NAME", "쿠폰명은 2자 이상 50자 이하이어야 합니다."),
    REQUIRED_COUPON_TYPE(HttpStatus.BAD_REQUEST, "REQUIRED_COUPON_TYPE", "쿠폰 유형은 필수 값입니다."),
    REQUIRED_DISCOUNT_VALUE(HttpStatus.BAD_REQUEST, "REQUIRED_DISCOUNT_VALUE", "할인 값은 필수 값입니다."),
    INVALID_DISCOUNT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_DISCOUNT_VALUE", "할인 값은 1 이상이어야 합니다."),
    INVALID_RATE_DISCOUNT_VALUE(HttpStatus.BAD_REQUEST, "INVALID_RATE_DISCOUNT_VALUE", "정률 쿠폰의 할인 비율은 1 이상 100 이하이어야 합니다."),
    REQUIRED_MAX_DISCOUNT_AMOUNT(HttpStatus.BAD_REQUEST, "REQUIRED_MAX_DISCOUNT_AMOUNT", "정률 쿠폰의 최대 할인 금액은 필수 값입니다."),
    REQUIRED_MIN_ORDER_PRICE(HttpStatus.BAD_REQUEST, "REQUIRED_MIN_ORDER_PRICE", "최소 주문 금액은 필수 값입니다."),
    REQUIRED_EXPIRED_AT(HttpStatus.BAD_REQUEST, "REQUIRED_EXPIRED_AT", "만료일은 필수 값입니다."),
    INVALID_EXPIRED_AT(HttpStatus.BAD_REQUEST, "INVALID_EXPIRED_AT", "만료일은 현재 시점 이후여야 합니다."),
    EXPIRED_COUPON(HttpStatus.BAD_REQUEST, "EXPIRED_COUPON", "만료된 쿠폰은 발급할 수 없습니다."),
    COUPON_SOLD_OUT(HttpStatus.BAD_REQUEST, "COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다."),
    ALREADY_COUPON_ISSUED(HttpStatus.BAD_REQUEST, "ALREADY_COUPON_ISSUED", "이미 발급받은 쿠폰입니다."),
    ALREADY_USED_COUPON(HttpStatus.BAD_REQUEST, "ALREADY_USED_COUPON", "이미 사용된 쿠폰입니다."),
    COUPON_MIN_ORDER_PRICE_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_MIN_ORDER_PRICE_NOT_MET", "쿠폰의 최소 주문 금액을 충족하지 못했습니다."),
    FORBIDDEN_COUPON_ACCESS(HttpStatus.FORBIDDEN, "FORBIDDEN_COUPON_ACCESS", "접근 권한이 없는 쿠폰입니다."),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다."),
    OWNED_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "OWNED_COUPON_NOT_FOUND", "보유 쿠폰을 찾을 수 없습니다."),
    COUPON_ISSUE_STATUS_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_ISSUE_STATUS_NOT_FOUND", "쿠폰 발급 상태를 찾을 수 없습니다."),

    /** Payment 도메인 에러 */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."),
    PAYMENT_NOT_READY(HttpStatus.BAD_REQUEST, "PAYMENT_NOT_READY", "결제 시작이 가능한 상태가 아닙니다."),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.BAD_REQUEST, "PAYMENT_ALREADY_PROCESSED", "이미 처리된 결제입니다."),
    PAYMENT_GATEWAY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_GATEWAY_UNAVAILABLE", "결제 서비스가 일시적으로 불가합니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
