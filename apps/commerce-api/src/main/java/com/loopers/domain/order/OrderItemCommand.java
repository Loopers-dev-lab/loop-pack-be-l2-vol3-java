package com.loopers.domain.order;

/**
 * 주문 항목 요청 커맨드.
 * <p>
 * 주문 생성 시 사용되는 주문 항목 요청 객체이다.
 * Controller에서 생성하여 Facade로 전달하며, Facade가 도메인 서비스에 위임할 때도 사용한다.
 * </p>
 *
 * @param productId 상품 ID
 * @param quantity  주문 수량
 */
public record OrderItemCommand(Long productId, int quantity) {
}
