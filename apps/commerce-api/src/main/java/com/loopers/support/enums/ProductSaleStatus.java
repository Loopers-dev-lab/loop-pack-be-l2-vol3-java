package com.loopers.support.enums;

/**
 * 상품 판매 상태. isOrderable()로 주문 가능 여부를 판별한다.
 */
public enum ProductSaleStatus {
    /** 판매 중 — 주문 가능 */
    ON_SALE,
    /** 일시 품절 — 주문 불가, 재입고 예정 */
    TEMP_SOLD_OUT,
    /** 판매 중지 — 주문 불가, 재개 미정 */
    STOPPED;

    /**
     * 현재 판매 상태에서 주문이 가능한지 판별한다. ON_SALE일 때만 true.
     *
     * @return 주문 가능하면 true
     */
    public boolean isOrderable() {
        return this == ON_SALE;
    }
}
