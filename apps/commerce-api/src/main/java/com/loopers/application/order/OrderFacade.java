package com.loopers.application.order;

import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.ProductValidationRequest;
import com.loopers.domain.product.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 유스케이스 조율.
 * 트랜잭션 경계, 도메인 결과 → OrderInfo 변환.
 * Controller는 Facade만 호출하며, request는 도메인 파라미터로 변환 후 Service에 전달한다.
 */
@Service
public class OrderFacade {

    private final OrderService orderService;

    public OrderFacade(OrderService orderService) {
        this.orderService = orderService;
    }

    @Transactional
    public OrderInfo create(Long userId, List<CreateOrderItemParam> params) {
        List<ProductValidationRequest> requests = params.stream()
                .map(p -> new ProductValidationRequest(p.productId(), Quantity.of(p.quantity()), p.optionId()))
                .toList();
        OrderModel order = orderService.create(userId, requests);
        return OrderInfo.from(order);
    }

    @Transactional(readOnly = true)
    public Optional<OrderInfo> findById(Long userId, Long orderId) {
        return orderService.findById(userId, orderId).map(OrderInfo::from);
    }

    @Transactional(readOnly = true)
    public List<OrderInfo> findOrders(Long userId, ZonedDateTime start, ZonedDateTime end, int page, int size) {
        return orderService.findOrders(userId, start, end, page, size).stream()
                .map(OrderInfo::from)
                .toList();
    }

    @Transactional
    public OrderInfo cancel(Long userId, Long orderId) {
        OrderModel order = orderService.cancel(userId, orderId);
        return OrderInfo.from(order);
    }
}
