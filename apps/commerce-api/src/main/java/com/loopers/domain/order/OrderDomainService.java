package com.loopers.domain.order;

import com.loopers.domain.order.exception.EmptyOrderItemException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Order 도메인 서비스
 * Order 도메인 내부의 규칙(생성/상태 전이)을 담당한다.
 * 도메인 간 협력(재고 조작 등)은 Application Layer에서 처리한다.
 */
@Component
public class OrderDomainService {

    public Order createOrder(Long userId, List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new EmptyOrderItemException();
        }
        return Order.create(userId, orderItems);
    }

    public boolean cancelOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("주문 정보는 필수입니다");
        }
        return order.cancel();
    }
}
