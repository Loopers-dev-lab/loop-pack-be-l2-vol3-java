package com.loopers.application.order;

/**
 * 주문 생성 시 항목 파라미터.
 * Controller DTO → 이 타입으로 변환 후 Facade에 전달. Facade에서 도메인 타입으로 변환.
 */
public record CreateOrderItemParam(Long productId, int quantity, Long optionId) {
}
