package com.loopers.support.common.error;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("ErrorType 테스트")
class ErrorTypeTest {

	@ParameterizedTest(name = "[{index}] {0} -> status={1}, code={2}, message={3}")
	@MethodSource("errorTypeProvider")
	@DisplayName("[ErrorType] 모든 enum 상수의 status, code, message가 올바르게 설정됨")
	void allEnumConstantsHaveCorrectValues(ErrorType errorType, HttpStatus expectedStatus,
		String expectedCode, String expectedMessage) {
		// Assert
		assertAll(
			() -> assertThat(errorType.getStatus()).isEqualTo(expectedStatus),
			() -> assertThat(errorType.getCode()).isEqualTo(expectedCode),
			() -> assertThat(errorType.getMessage()).isEqualTo(expectedMessage)
		);
	}


	static Stream<Arguments> errorTypeProvider() {
		return Stream.of(
			Arguments.of(ErrorType.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR,
				HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "일시적인 오류가 발생했습니다."),
			Arguments.of(ErrorType.BAD_REQUEST, HttpStatus.BAD_REQUEST,
				HttpStatus.BAD_REQUEST.getReasonPhrase(), "잘못된 요청입니다."),
			Arguments.of(ErrorType.NOT_FOUND, HttpStatus.NOT_FOUND,
				HttpStatus.NOT_FOUND.getReasonPhrase(), "존재하지 않는 요청입니다."),
			Arguments.of(ErrorType.CONFLICT, HttpStatus.CONFLICT,
				HttpStatus.CONFLICT.getReasonPhrase(), "이미 처리된 요청입니다."),
			Arguments.of(ErrorType.USER_ALREADY_EXISTS, HttpStatus.CONFLICT,
				"USER_ALREADY_EXISTS", "이미 가입된 로그인 ID입니다."),
			Arguments.of(ErrorType.INVALID_PASSWORD_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_PASSWORD_FORMAT", "비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다."),
			Arguments.of(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE, HttpStatus.BAD_REQUEST,
				"PASSWORD_CONTAINS_BIRTH_DATE", "비밀번호에 생년월일을 포함할 수 없습니다."),
			Arguments.of(ErrorType.INVALID_LOGIN_ID_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_LOGIN_ID_FORMAT", "로그인 ID는 영문과 숫자만 사용 가능하며, 4~20자여야 합니다."),
			Arguments.of(ErrorType.INVALID_NAME_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_NAME_FORMAT", "이름은 한글, 영문, 공백만 사용 가능하며, 최대 50자입니다."),
			Arguments.of(ErrorType.INVALID_EMAIL_FORMAT, HttpStatus.BAD_REQUEST,
				"INVALID_EMAIL_FORMAT", "올바른 이메일 형식이 아닙니다."),
			Arguments.of(ErrorType.INVALID_BIRTH_DATE, HttpStatus.BAD_REQUEST,
				"INVALID_BIRTH_DATE", "올바른 생년월일이 아닙니다."),
			Arguments.of(ErrorType.PASSWORD_SAME_AS_CURRENT, HttpStatus.BAD_REQUEST,
				"PASSWORD_SAME_AS_CURRENT", "새 비밀번호는 현재 비밀번호와 같을 수 없습니다."),
			Arguments.of(ErrorType.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED,
				"AUTHENTICATION_FAILED", "아이디와 비밀번호를 다시 확인해주세요."),

			// Catalog - Brand
			Arguments.of(ErrorType.BRAND_NOT_FOUND, HttpStatus.NOT_FOUND,
				"BRAND_NOT_FOUND", "브랜드가 존재하지 않습니다."),
			Arguments.of(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS, HttpStatus.CONFLICT,
				"BRAND_HAS_ACTIVE_PRODUCTS", "해당 브랜드에 활성 상품이 존재하여 삭제할 수 없습니다."),
			Arguments.of(ErrorType.BRAND_IS_VISIBLE, HttpStatus.CONFLICT,
				"BRAND_IS_VISIBLE", "노출 중인 브랜드는 삭제할 수 없습니다. 먼저 숨김 처리하세요."),
			Arguments.of(ErrorType.INVALID_BRAND_NAME, HttpStatus.BAD_REQUEST,
				"INVALID_BRAND_NAME", "올바른 브랜드명을 입력해주세요."),
			Arguments.of(ErrorType.INVALID_BRAND_DESCRIPTION, HttpStatus.BAD_REQUEST,
				"INVALID_BRAND_DESCRIPTION", "올바른 브랜드 설명을 입력해주세요."),
			Arguments.of(ErrorType.INVALID_BRAND_VISIBLE_STATUS, HttpStatus.BAD_REQUEST,
				"INVALID_BRAND_VISIBLE_STATUS", "올바른 노출 상태를 입력해주세요."),

			// Catalog - Product
			Arguments.of(ErrorType.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
				"PRODUCT_NOT_FOUND", "상품이 존재하지 않습니다."),
			Arguments.of(ErrorType.INVALID_PRODUCT_NAME, HttpStatus.BAD_REQUEST,
				"INVALID_PRODUCT_NAME", "올바른 상품명을 입력해주세요."),
			Arguments.of(ErrorType.INVALID_PRODUCT_PRICE, HttpStatus.BAD_REQUEST,
				"INVALID_PRODUCT_PRICE", "올바른 가격을 입력해주세요."),
			Arguments.of(ErrorType.INVALID_PRODUCT_STOCK, HttpStatus.BAD_REQUEST,
				"INVALID_PRODUCT_STOCK", "올바른 재고 수량을 입력해주세요."),
			Arguments.of(ErrorType.INVALID_PRODUCT_DESCRIPTION, HttpStatus.BAD_REQUEST,
				"INVALID_PRODUCT_DESCRIPTION", "올바른 상품 설명을 입력해주세요."),
			Arguments.of(ErrorType.PRODUCT_OUT_OF_STOCK, HttpStatus.CONFLICT,
				"PRODUCT_OUT_OF_STOCK", "재고가 부족합니다."),

			// Like
			Arguments.of(ErrorType.LIKE_NOT_FOUND, HttpStatus.NOT_FOUND,
				"LIKE_NOT_FOUND", "좋아요를 찾을 수 없습니다."),
			Arguments.of(ErrorType.LIKE_TARGET_NOT_FOUND, HttpStatus.NOT_FOUND,
				"LIKE_TARGET_NOT_FOUND", "좋아요 대상을 찾을 수 없습니다."),
			Arguments.of(ErrorType.INVALID_LIKE_TARGET, HttpStatus.BAD_REQUEST,
				"INVALID_LIKE_TARGET", "유효하지 않은 좋아요 대상입니다."),

			// Cart
			Arguments.of(ErrorType.CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND,
				"CART_ITEM_NOT_FOUND", "장바구니 항목을 찾을 수 없습니다."),
			Arguments.of(ErrorType.INVALID_QUANTITY, HttpStatus.BAD_REQUEST,
				"INVALID_QUANTITY", "유효하지 않은 수량입니다."),
			Arguments.of(ErrorType.CART_PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND,
				"CART_PRODUCT_NOT_FOUND", "장바구니에 담을 상품을 찾을 수 없습니다."),
			Arguments.of(ErrorType.CART_ADD_CONFLICT, HttpStatus.CONFLICT,
				"CART_ADD_CONFLICT", "장바구니 담기에 실패했습니다. 다시 시도해주세요."),

			// Order
			Arguments.of(ErrorType.ORDER_NOT_FOUND, HttpStatus.NOT_FOUND,
				"ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
			Arguments.of(ErrorType.EMPTY_CART, HttpStatus.BAD_REQUEST,
				"EMPTY_CART", "장바구니가 비어있습니다."),
			Arguments.of(ErrorType.INVALID_SNAPSHOT_NAME, HttpStatus.BAD_REQUEST,
				"INVALID_SNAPSHOT_NAME", "주문 상품 정보가 올바르지 않습니다."),
			Arguments.of(ErrorType.INVALID_SNAPSHOT_PRICE, HttpStatus.BAD_REQUEST,
				"INVALID_SNAPSHOT_PRICE", "주문 가격 정보가 올바르지 않습니다."),
			Arguments.of(ErrorType.INVALID_ORDER_QUANTITY, HttpStatus.BAD_REQUEST,
				"INVALID_ORDER_QUANTITY", "유효하지 않은 주문 수량입니다."),
			Arguments.of(ErrorType.INVALID_ORDER_TOTAL_PRICE, HttpStatus.BAD_REQUEST,
				"INVALID_ORDER_TOTAL_PRICE", "유효하지 않은 주문 총액입니다."),
			Arguments.of(ErrorType.INVALID_REQUEST_ID, HttpStatus.BAD_REQUEST,
				"INVALID_REQUEST_ID", "주문 요청 정보가 올바르지 않습니다."),
			Arguments.of(ErrorType.ORDER_EMPTY_ITEMS, HttpStatus.BAD_REQUEST,
				"ORDER_EMPTY_ITEMS", "주문 항목이 비어있습니다."),
			Arguments.of(ErrorType.ORDER_OUT_OF_STOCK, HttpStatus.CONFLICT,
				"ORDER_OUT_OF_STOCK", "재고가 부족하여 주문할 수 없습니다."),

			// 동시성
			Arguments.of(ErrorType.OPTIMISTIC_LOCK_CONFLICT, HttpStatus.CONFLICT,
				"OPTIMISTIC_LOCK_CONFLICT", "요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요."),
			Arguments.of(ErrorType.PESSIMISTIC_LOCK_CONFLICT, HttpStatus.CONFLICT,
				"PESSIMISTIC_LOCK_CONFLICT", "요청이 많아 처리하지 못했습니다. 잠시 후 다시 시도해주세요."),

			// Coupon - CouponTemplate
			Arguments.of(ErrorType.COUPON_TEMPLATE_NOT_FOUND, HttpStatus.NOT_FOUND,
				"COUPON_TEMPLATE_NOT_FOUND", "쿠폰이 존재하지 않습니다."),
			Arguments.of(ErrorType.INVALID_COUPON_NAME, HttpStatus.BAD_REQUEST,
				"INVALID_COUPON_NAME", "쿠폰 이름은 1~100자 입니다."),
			Arguments.of(ErrorType.INVALID_COUPON_VALUE, HttpStatus.BAD_REQUEST,
				"INVALID_COUPON_VALUE", "유효하지 않은 할인 값입니다."),
			Arguments.of(ErrorType.INVALID_COUPON_EXPIRED_AT, HttpStatus.BAD_REQUEST,
				"INVALID_COUPON_EXPIRED_AT", "만료일은 현재 시각 이후여야 합니다."),
			Arguments.of(ErrorType.COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT, HttpStatus.BAD_REQUEST,
				"COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT", "할인액이 최소 주문 금액보다 클 수 없습니다."),

			// Coupon - IssuedCoupon
			Arguments.of(ErrorType.ISSUED_COUPON_NOT_FOUND, HttpStatus.NOT_FOUND,
				"ISSUED_COUPON_NOT_FOUND", "발급된 쿠폰을 찾을 수 없습니다."),
			Arguments.of(ErrorType.COUPON_NOT_OWNED_BY_USER, HttpStatus.NOT_FOUND,
				"COUPON_NOT_OWNED_BY_USER", "발급된 쿠폰을 찾을 수 없습니다."),
			Arguments.of(ErrorType.COUPON_ALREADY_USED, HttpStatus.CONFLICT,
				"COUPON_ALREADY_USED", "이미 사용된 쿠폰입니다."),
			Arguments.of(ErrorType.COUPON_EXPIRED, HttpStatus.CONFLICT,
				"COUPON_EXPIRED", "만료된 쿠폰입니다."),
			Arguments.of(ErrorType.COUPON_MIN_ORDER_AMOUNT_NOT_MET, HttpStatus.BAD_REQUEST,
				"COUPON_MIN_ORDER_AMOUNT_NOT_MET", "최소 주문 금액 조건을 충족하지 못했습니다."),
			Arguments.of(ErrorType.COUPON_ISSUE_DUPLICATED, HttpStatus.CONFLICT,
				"COUPON_ISSUE_DUPLICATED", "이미 발급된 쿠폰입니다."),

			// CouponIssueRequest
			Arguments.of(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND, HttpStatus.NOT_FOUND,
				"COUPON_ISSUE_REQUEST_NOT_FOUND", "쿠폰 발급 요청을 찾을 수 없습니다."),
			Arguments.of(ErrorType.COUPON_SOLD_OUT, HttpStatus.CONFLICT,
				"COUPON_SOLD_OUT", "쿠폰이 모두 소진되었습니다."),

			// Order - Payment 연동
			Arguments.of(ErrorType.ORDER_NOT_PAYABLE, HttpStatus.BAD_REQUEST,
				"ORDER_NOT_PAYABLE", "주문이 결제 가능한 상태가 아닙니다."),

			// Payment
			Arguments.of(ErrorType.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND,
				"PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
			Arguments.of(ErrorType.PAYMENT_ALREADY_IN_PROGRESS, HttpStatus.CONFLICT,
				"PAYMENT_ALREADY_IN_PROGRESS", "이미 결제가 진행 중입니다."),
			Arguments.of(ErrorType.INVALID_CARD_TYPE, HttpStatus.BAD_REQUEST,
				"INVALID_CARD_TYPE", "지원하지 않는 카드 타입입니다."),
			Arguments.of(ErrorType.INVALID_CARD_NO, HttpStatus.BAD_REQUEST,
				"INVALID_CARD_NO", "잘못된 카드번호 형식입니다."),

			// PG 연동
			Arguments.of(ErrorType.PG_BAD_REQUEST, HttpStatus.BAD_REQUEST,
				"PG_BAD_REQUEST", "PG 요청이 올바르지 않습니다."),
			Arguments.of(ErrorType.PG_REQUEST_FAILED, HttpStatus.BAD_GATEWAY,
				"PG_REQUEST_FAILED", "PG 결제 요청에 실패했습니다."),
			Arguments.of(ErrorType.PG_SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
				"PG_SERVICE_UNAVAILABLE", "PG 서비스를 일시적으로 사용할 수 없습니다."),
			Arguments.of(ErrorType.PG_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT,
				"PG_TIMEOUT", "PG 응답 시간이 초과되었습니다."),

			// Ranking
			Arguments.of(ErrorType.INVALID_RANKING_PERIOD, HttpStatus.BAD_REQUEST,
				"INVALID_RANKING_PERIOD", "지원하지 않는 랭킹 기간입니다. 허용값: daily, weekly, monthly"),

			// Queue
			Arguments.of(ErrorType.INVALID_QUEUE_TOKEN, HttpStatus.UNAUTHORIZED,
				"INVALID_QUEUE_TOKEN", "유효하지 않은 대기열 토큰입니다."),
			Arguments.of(ErrorType.QUEUE_NOT_ENTERED, HttpStatus.NOT_FOUND,
				"QUEUE_NOT_ENTERED", "대기열에 진입하지 않았거나 아직 처리 중입니다."),
			Arguments.of(ErrorType.QUEUE_CAPACITY_EXCEEDED, HttpStatus.SERVICE_UNAVAILABLE,
				"QUEUE_CAPACITY_EXCEEDED", "대기열이 가득 찼습니다. 잠시 후 다시 시도해주세요."),
			Arguments.of(ErrorType.QUEUE_PRODUCE_FAILED, HttpStatus.INTERNAL_SERVER_ERROR,
				"QUEUE_PRODUCE_FAILED", "대기열 진입 요청에 실패했습니다."),
			Arguments.of(ErrorType.ORDER_ALREADY_IN_PROGRESS, HttpStatus.CONFLICT,
				"ORDER_ALREADY_IN_PROGRESS", "이미 주문이 처리 중입니다. 잠시 후 다시 시도해주세요.")
		);
	}


	@Test
	@DisplayName("[ErrorType] enum 상수 개수가 71개임을 보장")
	void enumConstantCount() {
		// Assert
		assertThat(ErrorType.values()).hasSize(71);
	}


	@Test
	@DisplayName("[ErrorType] errorTypeProvider가 모든 enum 상수를 포함")
	void errorTypeProviderCoversAllEnums() {
		// Act
		long providerCount = errorTypeProvider().count();

		// Assert
		assertThat(providerCount).isEqualTo(ErrorType.values().length);
	}

}
