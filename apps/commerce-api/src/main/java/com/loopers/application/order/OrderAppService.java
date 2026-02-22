package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
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

    @Transactional(readOnly = true)
    public Order getById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<Order> getByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Transactional
    public Order pay(Long orderId) {
        Order order = getById(orderId);
        order.pay();
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancel(Long orderId) {
        Order order = getById(orderId);
        order.cancel();
        return orderRepository.save(order);
    }

    @Transactional
    public Order prepare(Long orderId) {
        Order order = getById(orderId);
        order.prepare();
        return orderRepository.save(order);
    }

    @Transactional
    public Order ship(Long orderId) {
        Order order = getById(orderId);
        order.ship();
        return orderRepository.save(order);
    }

    @Transactional
    public Order deliver(Long orderId) {
        Order order = getById(orderId);
        order.deliver();
        return orderRepository.save(order);
    }
}
