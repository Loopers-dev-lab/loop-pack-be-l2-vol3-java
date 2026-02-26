package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
