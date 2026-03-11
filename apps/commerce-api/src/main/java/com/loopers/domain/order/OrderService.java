package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * 기존 단순 생성용 메서드 (다른 곳에서 사용 중일 수 있어 유지)
     */
    public Order createOrder(Long refUserId, Integer totalPrice) {
        Order order = Order.create(
            null,
            refUserId,
            OrderStatus.ORDERED,
            totalPrice,
            ZonedDateTime.now()
        );
        return orderRepository.save(order);
    }

    /**
     * 애그리거트 기준 주문 생성
     * - Order.place(...)를 호출해 애그리거트를 만들고, OrderRepository를 통해 저장한다.
     */
    public Order placeOrder(Long userId, List<OrderItemSpec> itemSpecs) {
        Order order = Order.place(userId, itemSpecs, ZonedDateTime.now());
        return orderRepository.save(order);
    }
}
