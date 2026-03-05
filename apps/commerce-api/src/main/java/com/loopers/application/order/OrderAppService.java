package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderAppService {
    private final OrderRepository orderRepository;

    @Transactional
    public Order create(Long userId, List<OrderItem> orderItems) {
        Order order = Order.create(userId, orderItems);
        return orderRepository.save(order);
    }

    @Transactional
    public Order create(Order order) {
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> getByUserId(Long userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        orders.forEach(order -> order.getOrderItems().size());
        return orders;
    }

    @Transactional
    public Order pay(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.pay();
        return order;
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.cancel();
        return order;
    }

    @Transactional
    public Order prepare(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.prepare();
        return order;
    }

    @Transactional
    public Order ship(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.ship();
        return order;
    }

    @Transactional
    public Order deliver(Long orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
        order.deliver();
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getAll() {
        List<Order> orders = orderRepository.findAll();
        orders.forEach(order -> order.getOrderItems().size());
        return orders;
    }

    @Transactional(readOnly = true)
    public Page<Order> getAll(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);
        orders.forEach(order -> order.getOrderItems().size());
        return orders;
    }
}
