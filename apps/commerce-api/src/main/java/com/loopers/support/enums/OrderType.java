package com.loopers.support.enums;

/**
 * 주문 유형. DIRECT 주문 취소/만료 시 장바구니 복원이 발생한다.
 */
public enum OrderType {
    /** 상품 상세에서 바로 주문 — 취소/만료 시 장바구니 자동 복원 */
    DIRECT,
    /** 장바구니에서 선택 주문 — 장바구니 유지 (복원 불필요) */
    CART
}
