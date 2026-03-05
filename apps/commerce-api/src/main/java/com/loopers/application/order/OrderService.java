package com.loopers.application.order;

import com.loopers.domain.order.*;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public void validateItems(List<OrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }

        boolean hasInvalidQuantity = items.stream().anyMatch(item -> item.quantity() <= 0);
        if (hasInvalidQuantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다.");
        }

        long distinctCount = items.stream().map(OrderItemCommand::productId).distinct().count();
        if (distinctCount != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함되어 있습니다.");
        }
    }

    @Transactional
    public OrderInfo placeOrder(Long userId, List<OrderItemSnapshot> snapshots) {
        return placeOrder(userId, snapshots, 0L);
    }

    @Transactional
    public OrderInfo placeOrder(Long userId, List<OrderItemSnapshot> snapshots, Long discountAmount) {
        Order order = orderRepository.save(Order.create(userId, snapshots, discountAmount));

        List<OrderItem> orderItems = snapshots.stream()
                                              .map(s -> OrderItem.create(
                                                      order.getId(),
                                                      s.productId(),
                                                      s.productName(),
                                                      s.unitPrice(),
                                                      s.quantity()
                                              ))
                                              .toList();
        orderItemRepository.saveAll(orderItems);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> getOrders(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findByUserIdAndCreatedAtBetween(userId, startAt, endAt)
                              .stream()
                              .map(OrderInfo::from)
                              .toList();
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                                     .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));

        if (!order.getUserId().equals(userId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.");
        }

        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderItemInfo> getOrderItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId)
                                  .stream()
                                  .map(OrderItemInfo::from)
                                  .toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderInfo> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                                     .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        return OrderInfo.from(order);
    }
}
