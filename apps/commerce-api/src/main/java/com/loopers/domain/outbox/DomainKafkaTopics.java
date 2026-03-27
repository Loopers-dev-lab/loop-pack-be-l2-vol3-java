package com.loopers.domain.outbox;

/**
 * 쿠폰 도메인을 제외한 시스템 간 전파용 Kafka 토픽명.
 * 상세는 {@code skills/kafka/KAFKA_APPLICATION.md} §0.4.
 */
public final class DomainKafkaTopics {

    public static final String PRODUCT_EVENTS = "product-events";
    public static final String ORDER_EVENTS = "order-events";
    /** 회원·브랜드·장바구니 등 비상품 집계용 이벤트(멱등만 수취·알림/분석 연동에 사용). */
    public static final String USER_EVENTS = "user-events";

    private DomainKafkaTopics() {
    }
}
