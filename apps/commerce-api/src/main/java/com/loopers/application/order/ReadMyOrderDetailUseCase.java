package com.loopers.application.order;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자가 자신의 주문 상세를 조회합니다.
 *
 * <p>주문 항목을 포함한 상세 정보를 반환하며, 본인의 주문이 아닌 경우 예외가 발생합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadMyOrderDetailUseCase {

    private final OrderService orderService;

    /**
     * @param userId 사용자 ID
     * @param orderId 주문 ID
     * @return 주문 상세 정보 (주문 항목 포함)
     */
    @Transactional(readOnly = true)
    public OrderDetailResult execute(Long userId, Long orderId) {
        Order order = orderService.getMyOrder(userId, orderId);
        return OrderDetailResult.from(order);
    }
}
