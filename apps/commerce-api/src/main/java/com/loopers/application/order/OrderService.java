package com.loopers.application.order;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemRepository;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public void validateItems(List<OrderItemCommand> items) {
        if (items == null || items.isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 항목이 비어있습니다.");
        }
        long distinctCount = items.stream().map(OrderItemCommand::productId).distinct().count();
        if (distinctCount != items.size()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "중복된 상품이 포함되어 있습니다.");
        }


    }

    @Transactional
    public OrderInfo createOrder(Long userId, List<ProductInfo> products, List<OrderItemCommand> items) {
        Map<Long, ProductInfo> productMap = products.stream()
                                                    .collect(Collectors.toMap(ProductInfo::id, p -> p));
        List<OrderItemSnapshot> snapshots = items.stream()
                                                 .map(item -> {
                                                     ProductInfo p = productMap.get(item.productId());
                                                     return new OrderItemSnapshot(
                                                             p.id(),
                                                             p.name(),
                                                             p.price(),
                                                             item.quantity()
                                                     );
                                                 })
                                                 .toList();
        long totalAmount = snapshots.stream()
                                    .mapToLong(OrderItemSnapshot::lineAmount)
                                    .sum();
        Order order = orderRepository.save(Order.create(userId, totalAmount));

        List<OrderItem> orderItems = snapshots.stream()
                                              .map(s -> OrderItem.create(
                                                      order.getId(),
                                                      s.productId(),
                                                      s.productName(),
                                                      s.unitPrice(),
                                                      s.quantity()
                                              ))
                                              .toList();
        orderItems.forEach(orderItemRepository::save);

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
