package com.loopers.domain.order;

/**
 * 주문 상태.
 */
public enum OrderStatus {
    /**
     * 주문 생성됨
     */
    CREATED,

    /**
     * 결제 완료
     */
    PAID,

    /**
     * 배송중
     */
    SHIPPED,

    /**
     * 배송 완료
     */
    DELIVERED,

    /**
     * 취소됨
     */
    CANCELLED
}
