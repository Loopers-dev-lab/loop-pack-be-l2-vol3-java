package com.loopers.domain.product;

/**
 * 재고 복구 항목(주문 취소 등에서 사용).
 * 도메인·application 전용 타입.
 *
 * @param productId 상품 ID
 * @param quantity  복구할 수량
 */
public record RestoreStockItem(Long productId, int quantity) {
}
