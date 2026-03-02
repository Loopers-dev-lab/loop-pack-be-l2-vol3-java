package com.loopers.domain.product;

/**
 * 상품 검증 요청(주문/장바구니 등에서 사용).
 * 도메인·application 전용 타입이며, interfaces DTO와 동일 타입 재사용 금지.
 *
 * @param productId 상품 ID
 * @param quantity  수량 (1 이상)
 * @param optionId  옵션 ID (옵션 테이블 없음, 값 보존만)
 */
public record ProductValidationRequest(Long productId, Quantity quantity, Long optionId) {
}
