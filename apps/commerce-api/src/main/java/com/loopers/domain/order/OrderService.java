package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderModel save(OrderModel order) {
        return orderRepository.save(order);
    }

    @Transactional
    public List<OrderItemModel> saveAllItems(List<OrderItemModel> orderItems) {
        return orderItemRepository.saveAll(orderItems);
    }

    @Transactional(readOnly = true)
    public OrderModel getOrder(Long orderId, Long userId) {
        OrderModel order = findById(orderId);
        if (!order.userId().equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 주문만 조회할 수 있습니다.");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public OrderModel getOrderForAdmin(Long orderId) {
        return findById(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderModel> getOrdersByUser(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderRepository.findAllByUserIdAndCreatedAtBetween(userId, startAt, endAt);
    }

    @Transactional(readOnly = true)
    public Page<OrderModel> getAllForAdmin(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<OrderItemModel> getOrderItems(Long orderId) {
        return orderItemRepository.findAllByOrderId(orderId);
    }

    private OrderModel findById(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }
}
