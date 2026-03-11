package com.loopers.support.error;

/**
 * 예외 메시지를 중앙에서 관리하는 클래스
 * 프로덕션 코드와 테스트 코드에서 공통으로 사용
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * User 도메인 관련 에러 메시지
     */
    public static final class User {
        private User() {}

        public static final String LOGIN_ID_ALREADY_EXISTS = "이미 사용 중인 아이디입니다.";
        public static final String EMAIL_ALREADY_EXISTS = "이미 가입된 이메일입니다.";
        public static final String INVALID_LOGIN_INFO = "로그인 정보가 올바르지 않습니다.";
        public static final String USER_NOT_FOUND = "사용자를 찾을 수 없습니다.";
        public static final String CURRENT_PASSWORD_MISMATCH = "기존 비밀번호가 일치하지 않습니다.";
        public static final String NEW_PASSWORD_SAME_AS_CURRENT = "새 비밀번호는 기존 비밀번호와 달라야 합니다.";
        public static final String PASSWORD_CONTAINS_BIRTH_DATE = "비밀번호에 생년월일을 포함할 수 없습니다.";
        public static final String LOGIN_ID_REQUIRED = "로그인 ID는 필수입니다.";
        public static final String PASSWORD_REQUIRED = "비밀번호는 필수입니다.";
        public static final String BIRTH_DATE_REQUIRED = "생년월일은 필수입니다.";
        public static final String NAME_REQUIRED = "이름은 필수입니다.";
        public static final String EMAIL_REQUIRED = "이메일은 필수입니다.";
    }

    /**
     * Money VO 관련 에러 메시지
     */
    public static final class Money {
        private Money() {}

        public static final String AMOUNT_INVALID = "금액은 null이거나 음수가 될 수 없습니다";
    }

    /**
     * Product 도메인 관련 에러 메시지
     */
    public static final class Product {
        private Product() {}

        public static final String PRODUCT_NOT_FOUND = "상품을 찾을 수 없습니다";
        public static final String CREATE_PRODUCT_REQUEST_REQUIRED = "상품 생성 요청은 필수입니다";
        public static final String PRODUCT_ID_LIST_REQUIRED = "상품 ID 목록은 필수입니다";
        public static final String PRODUCT_ID_LIST_CONTAINS_INVALID = "존재하지 않는 상품이 포함되어 있습니다";
        public static final String SEARCH_CONDITION_REQUIRED = "검색 조건은 필수입니다";
        public static final String PRODUCT_NAME_REQUIRED = "상품 이름은 필수 입니다";
        public static final String BRAND_ID_INVALID = "브랜드FK는 null이거나 0이하가 될 수 없습니다";
        public static final String STOCK_INVALID = "상품 재고는 null이거나 음수가 될 수 없습니다";
        public static final String LIKE_COUNT_INVALID = "좋아요 수는 null이거나 음수가 될 수 없습니다";
        public static final String INSUFFICIENT_STOCK = "재고가 부족합니다";
        public static final String LIKE_COUNT_NEGATIVE = "좋아요 갯수는 음수가 될 수 없습니다";
        public static final String QUANTITY_MUST_BE_POSITIVE = "수량은 양수여야 합니다";
    }

    /**
     * Brand 도메인 관련 에러 메시지
     */
    public static final class Brand {
        private Brand() {}

        public static final String BRAND_NOT_FOUND = "브랜드를 찾을 수 없습니다";
        public static final String BRAND_NAME_REQUIRED = "브랜드 이름은 필수 입니다";
    }

    /**
     * Order 도메인 관련 에러 메시지
     */
    public static final class Order {
        private Order() {}

        public static final String ORDER_ITEMS_EMPTY = "주문할 상품이 없습니다";
        public static final String ORDER_QUANTITIES_EMPTY = "주문 수량 정보가 없습니다";
        public static final String ORDER_ID_INVALID = "주문FK는 null이거나 0이하가 될 수 없습니다";
        public static final String ORDER_STATUS_REQUIRED = "주문 상태는 필수입니다";
        public static final String ORDER_STATUS_CHANGE_DT_REQUIRED = "주문 상태 변경 일시는 필수입니다";
        public static final String PRODUCT_ID_INVALID = "상품FK는 null이거나 0이하가 될 수 없습니다";
        public static final String USER_ID_INVALID = "유저FK는 null이거나 0이하가 될 수 없습니다";
        public static final String ORDER_DT_REQUIRED = "주문 일시는 필수입니다";
        public static final String CANCEL_ONLY_WHEN_COMPLETED = "주문 완료 상태에서만 취소할 수 있습니다";
        public static final String QUANTITY_MUST_BE_POSITIVE = "수량은 양수여야 합니다";
    }

    /**
     * Like 도메인 관련 에러 메시지
     */
    public static final class Like {
        private Like() {}

        public static final String ALREADY_LIKED = "이미 좋아요를 누른 상품입니다";
        public static final String LIKE_NOT_FOUND = "좋아요 객체를 찾을 수 없습니다";
        public static final String PRODUCT_ID_INVALID = "상품FK는 null이거나 음수가 될 수 없습니다";
        public static final String USER_ID_INVALID = "유저FK는 null이거나 음수가 될 수 없습니다";
    }

    /**
     * Example 도메인 관련 에러 메시지
     */
    public static final class Example {
        private Example() {}

        public static final String EXAMPLE_NOT_FOUND = "예시를 찾을 수 없습니다.";
        public static final String NAME_REQUIRED = "이름은 비어있을 수 없습니다.";
        public static final String DESCRIPTION_REQUIRED = "설명은 비어있을 수 없습니다.";
    }

    /**
     * Auth 관련 에러 메시지
     */
    public static final class Auth {
        private Auth() {}

        public static final String AUTH_HEADER_MISSING = "인증 헤더가 누락되었습니다.";
    }
}
