package com.loopers.domain.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public Order createOrder(Long userId, Money totalPrice, List<OrderItemCommand> itemCommands) {
        Order order = new Order(userId, totalPrice);
        Order savedOrder = orderRepository.save(order);

        List<OrderItem> items = itemCommands.stream()
            .map(cmd -> new OrderItem(
                savedOrder.getId(), cmd.productId(), cmd.productName(),
                cmd.productPrice(), cmd.brandName(), cmd.quantity()
            ))
            .toList();
        items.forEach(orderItemRepository::save);

        return savedOrder;
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Order getByIdAndUserId(Long id, Long userId) {
        Order order = getById(id);
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        return order;
    }

    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    public PageResult<Order> getMyOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, int page, int size) {
        return orderRepository.findByUserIdAndCreatedAtBetween(userId, startAt, endAt, page, size);
    }

    public PageResult<Order> getAllOrders(int page, int size) {
        return orderRepository.findAll(page, size);
    }
}
