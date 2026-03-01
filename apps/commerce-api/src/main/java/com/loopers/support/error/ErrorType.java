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

    /** Like 도메인 에러 */
    REQUIRED_USER_ID(HttpStatus.BAD_REQUEST, "REQUIRED_USER_ID", "사용자 ID는 필수 값입니다."),
    REQUIRED_PRODUCT_ID(HttpStatus.BAD_REQUEST, "REQUIRED_PRODUCT_ID", "상품 ID는 필수 값입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
