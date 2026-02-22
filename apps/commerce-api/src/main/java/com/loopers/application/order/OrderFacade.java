package com.loopers.application.order;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {
    private final OrderAppService orderAppService;
    private final ProductAppService productAppService;

    public Order createOrder(OrderCreateCommand command) {
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderCreateCommand.OrderItemCommand itemCommand : command.getItems()) {
            Product product = productAppService.decreaseStock(
                    itemCommand.getProductId(),
                    itemCommand.getQuantity()
            );

            OrderItem orderItem = OrderItem.of(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    itemCommand.getQuantity()
            );
            orderItems.add(orderItem);
        }

        return orderAppService.create(command.getUserId(), orderItems);
    }

    public Order cancelOrder(Long orderId) {
        Order order = orderAppService.getById(orderId);

        for (OrderItem item : order.getOrderItems()) {
            productAppService.increaseStock(item.getProductId(), item.getQuantity());
        }

        return orderAppService.cancel(orderId);
    }

    public Order getOrder(Long orderId) {
        return orderAppService.getById(orderId);
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderAppService.getByUserId(userId);
    }

    public Order payOrder(Long orderId) {
        return orderAppService.pay(orderId);
    }

    public Order prepareOrder(Long orderId) {
        return orderAppService.prepare(orderId);
    }

    public Order shipOrder(Long orderId) {
        return orderAppService.ship(orderId);
    }

    public Order deliverOrder(Long orderId) {
        return orderAppService.deliver(orderId);
    }
}
