package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderHistoryService orderHistoryService;

    @Transactional
    public Order createOrder(Long userId, List<OrderItem> orderItems, BigDecimal discountAmount, Long userCouponId) {
        Order order = Order.create(userId, orderItems, discountAmount, userCouponId);
        Order savedOrder = orderRepository.save(order);
        orderHistoryService.recordHistory(savedOrder.getId(), null, OrderStatus.CREATED, "주문 생성");
        return savedOrder;
    }

    public Order getById(Long id) {
        return orderRepository.findActiveById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    public Page<Order> getOrdersByUserId(Long userId, Pageable pageable) {
        return orderRepository.findAllActiveByUserId(userId, pageable);
    }

    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAllActive(pageable);
    }
}
