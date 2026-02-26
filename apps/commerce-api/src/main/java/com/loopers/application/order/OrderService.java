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
@Transactional(readOnly = true)
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

        return orderRepository.save(order);
    }

    // Query

    public Order findOrderById(Long orderId, Long userId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다"));

        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 주문입니다");
        }

        return order;
    }

    public Page<Order> findOrdersByUserIdAndDateRange(Long userId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        return orderRepository.findAllByUserIdAndCreatedAtBetween(userId, startDate, endDate, pageable);
    }
}
