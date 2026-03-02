package com.loopers.domain.order;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.Money;
import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@DomainService
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order create(Cart cart, Money discountAmount, Long ownedCouponId) {
        Order order = Order.create(cart, discountAmount, ownedCouponId);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getMyOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.ORDER_NOT_FOUND));
        order.validateOwner(userId);
        return order;
    }
}
