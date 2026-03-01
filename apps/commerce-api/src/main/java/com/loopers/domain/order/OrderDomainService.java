package com.loopers.domain.order;

import com.loopers.domain.PageResult;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
public class OrderDomainService {

    private final OrderRepository orderRepository;

    public Order createOrder(Long userId, List<OrderItemCommand> itemCommands) {
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 하나 이상이어야 합니다.");
        }

        List<Long> productIds = itemCommands.stream().map(OrderItemCommand::productId).toList();
        OrderPolicy.validateNoDuplicateProducts(productIds);

        Money totalPrice = calculateTotalPrice(itemCommands);

        Order order = new Order(userId, totalPrice);
        order.addItems(itemCommands);

        return orderRepository.save(order);
    }

    public Order createOrder(Long userId, List<OrderItemCommand> itemCommands,
                             Money originalPrice, Money discountAmount, Long couponIssueId) {
        if (itemCommands == null || itemCommands.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목은 하나 이상이어야 합니다.");
        }

        List<Long> productIds = itemCommands.stream().map(OrderItemCommand::productId).toList();
        OrderPolicy.validateNoDuplicateProducts(productIds);

        Order order = new Order(userId, originalPrice, discountAmount, couponIssueId);
        order.addItems(itemCommands);

        return orderRepository.save(order);
    }

    private Money calculateTotalPrice(List<OrderItemCommand> itemCommands) {
        Money total = new Money(0);
        for (OrderItemCommand cmd : itemCommands) {
            total = total.plus(cmd.productPrice().multiply(cmd.quantity()));
        }
        return total;
    }

    public Order getById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Order getByIdWithItems(Long id) {
        return orderRepository.findByIdWithItems(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Order getByIdAndUserId(Long id, Long userId) {
        Order order = getById(id);
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        return order;
    }

    public Order getByIdAndUserIdWithItems(Long id, Long userId) {
        Order order = getByIdWithItems(id);
        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }
        return order;
    }

    public PageResult<Order> getMyOrders(Long userId, LocalDate startAt, LocalDate endAt, int page, int size) {
        ZonedDateTime start = startAt.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end = endAt.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        return orderRepository.findByUserIdAndCreatedAtBetween(userId, start, end, page, size);
    }

    public PageResult<Order> getAllOrders(int page, int size) {
        return orderRepository.findAll(page, size);
    }
}
