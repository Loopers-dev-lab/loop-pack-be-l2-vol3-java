package com.loopers.domain.outbox;

/**
 * Outbox/Kafka 이벤트 계약 상수 집합(토픽명·eventType 문자열).
 * 시스템 간 전파 토픽 상세는 {@code skills/kafka/KAFKA_APPLICATION.md} §0.4.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    public static final class Topic {
        public static final String PRODUCT_EVENTS = "product-events";
        public static final String ORDER_EVENTS = "order-events";
        /** 회원·브랜드·장바구니 등 비상품 집계용 이벤트(멱등만 수취·알림/분석 연동에 사용). */
        public static final String USER_EVENTS = "user-events";
        /** 선착순/비동기 쿠폰 발급 요청 커맨드 큐. */
        public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";
        /** Redis 장애 시 대기열 진입 의도를 복구하기 위한 커맨드 큐. */
        public static final String QUEUE_JOIN_FALLBACK = "queue-join-fallback";

        private Topic() {
        }
    }

    /** Outbox {@code event_type} 및 Consumer 라우팅용 문자열. */
    public static final class Type {
        public static final String PRODUCT_LIKE_CHANGED = "PRODUCT_LIKE_CHANGED";
        public static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
        public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
        public static final String USER_REGISTERED = "USER_REGISTERED";
        public static final String BRAND_REGISTERED = "BRAND_REGISTERED";
        public static final String CART_ITEM_ADDED = "CART_ITEM_ADDED";
        public static final String COUPON_ISSUE_REQUESTED = "COUPON_ISSUE_REQUESTED";
        public static final String QUEUE_JOIN_FALLBACK_REQUESTED = "QUEUE_JOIN_FALLBACK_REQUESTED";

        private Type() {
        }
    }
}
