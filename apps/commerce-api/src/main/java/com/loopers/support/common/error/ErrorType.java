package com.loopers.support.common.error;


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
	CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.getReasonPhrase(), "이미 처리된 요청입니다."),

	/** User 도메인 에러 */
	USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "이미 가입된 로그인 ID입니다."),
	INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD_FORMAT", "비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다."),
	PASSWORD_CONTAINS_BIRTH_DATE(HttpStatus.BAD_REQUEST, "PASSWORD_CONTAINS_BIRTH_DATE", "비밀번호에 생년월일을 포함할 수 없습니다."),
	INVALID_LOGIN_ID_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_LOGIN_ID_FORMAT", "로그인 ID는 영문과 숫자만 사용 가능하며, 4~20자여야 합니다."),
	INVALID_NAME_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_NAME_FORMAT", "이름은 한글, 영문, 공백만 사용 가능하며, 최대 50자입니다."),
	INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "INVALID_EMAIL_FORMAT", "올바른 이메일 형식이 아닙니다."),
	INVALID_BIRTH_DATE(HttpStatus.BAD_REQUEST, "INVALID_BIRTH_DATE", "올바른 생년월일이 아닙니다."),
	PASSWORD_SAME_AS_CURRENT(HttpStatus.BAD_REQUEST, "PASSWORD_SAME_AS_CURRENT", "새 비밀번호는 현재 비밀번호와 같을 수 없습니다."),

	/** 인증 에러 */
	AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "아이디와 비밀번호를 다시 확인해주세요."),

	/** Catalog - Brand 에러 */
	BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "BRAND_NOT_FOUND", "브랜드가 존재하지 않습니다."),
	BRAND_HAS_ACTIVE_PRODUCTS(HttpStatus.CONFLICT, "BRAND_HAS_ACTIVE_PRODUCTS", "해당 브랜드에 활성 상품이 존재하여 삭제할 수 없습니다."),
	BRAND_IS_VISIBLE(HttpStatus.CONFLICT, "BRAND_IS_VISIBLE", "노출 중인 브랜드는 삭제할 수 없습니다. 먼저 숨김 처리하세요."),
	INVALID_BRAND_NAME(HttpStatus.BAD_REQUEST, "INVALID_BRAND_NAME", "올바른 브랜드명을 입력해주세요."),
	INVALID_BRAND_DESCRIPTION(HttpStatus.BAD_REQUEST, "INVALID_BRAND_DESCRIPTION", "올바른 브랜드 설명을 입력해주세요."),
	INVALID_BRAND_VISIBLE_STATUS(HttpStatus.BAD_REQUEST, "INVALID_BRAND_VISIBLE_STATUS", "올바른 노출 상태를 입력해주세요."),

	/** Catalog - Product 에러 */
	PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품이 존재하지 않습니다."),
	INVALID_PRODUCT_NAME(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_NAME", "올바른 상품명을 입력해주세요."),
	INVALID_PRODUCT_PRICE(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_PRICE", "올바른 가격을 입력해주세요."),
	INVALID_PRODUCT_STOCK(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_STOCK", "올바른 재고 수량을 입력해주세요."),
	INVALID_PRODUCT_DESCRIPTION(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_DESCRIPTION", "올바른 상품 설명을 입력해주세요."),
	PRODUCT_OUT_OF_STOCK(HttpStatus.CONFLICT, "PRODUCT_OUT_OF_STOCK", "재고가 부족합니다."),

	/** Like 에러 */
	LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE_NOT_FOUND", "좋아요를 찾을 수 없습니다."),
	LIKE_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "LIKE_TARGET_NOT_FOUND", "좋아요 대상을 찾을 수 없습니다."),
	INVALID_LIKE_TARGET(HttpStatus.BAD_REQUEST, "INVALID_LIKE_TARGET", "유효하지 않은 좋아요 대상입니다."),

	/** Cart 에러 */
	CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다."),
	INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "유효하지 않은 수량입니다."),
	CART_PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "CART_PRODUCT_NOT_FOUND", "장바구니에 담을 상품을 찾을 수 없습니다."),
	CART_ADD_CONFLICT(HttpStatus.CONFLICT, "CART_ADD_CONFLICT", "장바구니 담기에 실패했습니다. 다시 시도해주세요."),

	/** Order 에러 */
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
	EMPTY_CART(HttpStatus.BAD_REQUEST, "EMPTY_CART", "장바구니가 비어있습니다."),
	INVALID_SNAPSHOT_NAME(HttpStatus.BAD_REQUEST, "INVALID_SNAPSHOT_NAME", "주문 상품 정보가 올바르지 않습니다."),
	INVALID_SNAPSHOT_PRICE(HttpStatus.BAD_REQUEST, "INVALID_SNAPSHOT_PRICE", "주문 가격 정보가 올바르지 않습니다."),
	INVALID_ORDER_QUANTITY(HttpStatus.BAD_REQUEST, "INVALID_ORDER_QUANTITY", "유효하지 않은 주문 수량입니다."),
	INVALID_ORDER_TOTAL_PRICE(HttpStatus.BAD_REQUEST, "INVALID_ORDER_TOTAL_PRICE", "유효하지 않은 주문 총액입니다."),
	INVALID_REQUEST_ID(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_ID", "주문 요청 정보가 올바르지 않습니다."),
	ORDER_EMPTY_ITEMS(HttpStatus.BAD_REQUEST, "ORDER_EMPTY_ITEMS", "주문 항목이 비어있습니다."),
	ORDER_OUT_OF_STOCK(HttpStatus.CONFLICT, "ORDER_OUT_OF_STOCK", "재고가 부족하여 주문할 수 없습니다."),

	/** 동시성 에러 (GlobalExceptionHandler 안전망 — 예상 지점에서 try-catch로 비즈니스 메시지 전달) */
	OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT",
		"요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."),
	PESSIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "PESSIMISTIC_LOCK_CONFLICT",
		"요청이 많아 처리하지 못했습니다. 잠시 후 다시 시도해주세요."),

	/** Coupon - CouponTemplate 에러 */
	COUPON_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_TEMPLATE_NOT_FOUND", "쿠폰이 존재하지 않습니다."),
	INVALID_COUPON_NAME(HttpStatus.BAD_REQUEST, "INVALID_COUPON_NAME", "쿠폰 이름은 1~100자 입니다."),
	INVALID_COUPON_VALUE(HttpStatus.BAD_REQUEST, "INVALID_COUPON_VALUE", "유효하지 않은 할인 값입니다."),
	INVALID_COUPON_EXPIRED_AT(HttpStatus.BAD_REQUEST, "INVALID_COUPON_EXPIRED_AT", "만료일은 현재 시각 이후여야 합니다."),
	COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT(HttpStatus.BAD_REQUEST, "COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT", "할인액이 최소 주문 금액보다 클 수 없습니다."),

	/** Coupon - IssuedCoupon 에러 */
	ISSUED_COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "ISSUED_COUPON_NOT_FOUND", "발급된 쿠폰을 찾을 수 없습니다."),
	COUPON_NOT_OWNED_BY_USER(HttpStatus.NOT_FOUND, "COUPON_NOT_OWNED_BY_USER", "발급된 쿠폰을 찾을 수 없습니다."),
	COUPON_ALREADY_USED(HttpStatus.CONFLICT, "COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
	COUPON_EXPIRED(HttpStatus.CONFLICT, "COUPON_EXPIRED", "만료된 쿠폰입니다."),
	COUPON_MIN_ORDER_AMOUNT_NOT_MET(HttpStatus.BAD_REQUEST, "COUPON_MIN_ORDER_AMOUNT_NOT_MET", "최소 주문 금액 조건을 충족하지 못했습니다."),
	COUPON_ISSUE_DUPLICATED(HttpStatus.CONFLICT, "COUPON_ISSUE_DUPLICATED", "이미 발급된 쿠폰입니다."),

	/** Coupon - CouponIssueRequest 에러 */
	COUPON_ISSUE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "COUPON_ISSUE_REQUEST_NOT_FOUND", "쿠폰 발급 요청을 찾을 수 없습니다."),
	COUPON_SOLD_OUT(HttpStatus.CONFLICT, "COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다."),

	/** Order - Payment 연동 에러 */
	ORDER_NOT_PAYABLE(HttpStatus.BAD_REQUEST, "ORDER_NOT_PAYABLE", "주문이 결제 가능한 상태가 아닙니다."),

	/** Payment 도메인 에러 */
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
	PAYMENT_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "PAYMENT_ALREADY_IN_PROGRESS", "이미 결제가 진행 중입니다."),
	INVALID_CARD_TYPE(HttpStatus.BAD_REQUEST, "INVALID_CARD_TYPE", "지원하지 않는 카드 타입입니다."),
	INVALID_CARD_NO(HttpStatus.BAD_REQUEST, "INVALID_CARD_NO", "잘못된 카드번호 형식입니다."),

	/** PG 연동 에러 */
	PG_BAD_REQUEST(HttpStatus.BAD_REQUEST, "PG_BAD_REQUEST", "PG 요청이 올바르지 않습니다."),
	PG_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "PG_REQUEST_FAILED", "PG 결제 요청에 실패했습니다."),
	PG_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PG_SERVICE_UNAVAILABLE", "PG 서비스를 일시적으로 사용할 수 없습니다."),
	PG_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "PG_TIMEOUT", "PG 응답 시간이 초과되었습니다."),

	/** Ranking 도메인 에러 */
	INVALID_RANKING_PERIOD(HttpStatus.BAD_REQUEST, "INVALID_RANKING_PERIOD", "지원하지 않는 랭킹 기간입니다. 허용값: daily, weekly, monthly"),

	/** Queue 도메인 에러 */
	INVALID_QUEUE_TOKEN(HttpStatus.UNAUTHORIZED, "INVALID_QUEUE_TOKEN", "유효하지 않은 대기열 토큰입니다."),
	QUEUE_NOT_ENTERED(HttpStatus.NOT_FOUND, "QUEUE_NOT_ENTERED", "대기열에 진입하지 않았거나 아직 처리 중입니다."),
	QUEUE_CAPACITY_EXCEEDED(HttpStatus.SERVICE_UNAVAILABLE, "QUEUE_CAPACITY_EXCEEDED", "대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요."),
	QUEUE_PRODUCE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "QUEUE_PRODUCE_FAILED", "대기열 진입 요청에 실패했습니다."),
	ORDER_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "ORDER_ALREADY_IN_PROGRESS", "이미 주문이 처리 중입니다. 잠시 후 다시 시도해주세요.");

	private final HttpStatus status;
	private final String code;
	private final String message;
}
