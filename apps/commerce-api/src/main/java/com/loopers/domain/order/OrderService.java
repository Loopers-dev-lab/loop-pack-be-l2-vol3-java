package com.loopers.domain.order;

import com.loopers.domain.common.Money;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * 애그리거트 기준 주문 생성 (쿠폰 적용)
     */
    @Transactional
    public Order placeOrder(Long userId, List<OrderItemSpec> itemSpecs, Long couponId, Money discountAmount) {
        Order order = Order.place(userId, itemSpecs, couponId, discountAmount, ZonedDateTime.now());
        return orderRepository.save(order);
    }
}
