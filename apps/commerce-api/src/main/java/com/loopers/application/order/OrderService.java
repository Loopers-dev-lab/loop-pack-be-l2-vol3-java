package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    // Command

    @Transactional
    public Order createOrder(OrderCommand.Create command) {
        Order order = Order.create(command.userId());

        command.items().forEach(item ->
                order.addItem(
                        item.productId(),
                        item.productName(),
                        item.price(),
                        item.quantity()
                )
        );

        if (command.coupon() != null) {
            order.applyCoupon(
                    command.coupon().issuedCouponId(),
                    command.coupon().discountAmount()
            );
        }

        return orderRepository.save(order);
    }

    // Query

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다"));
    }

    @Transactional(readOnly = true)
    public Page<Order> findOrdersByUserIdAndDateRange(Long userId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        return orderRepository.findAllByUserIdAndCreatedAtBetween(userId, startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Order> findAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }
}
