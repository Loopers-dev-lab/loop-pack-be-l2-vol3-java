package com.loopers.application.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderDomainService.OrderLineRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class OrderService {

    private final OrderDomainService orderDomainService;

    @Transactional
    public OrderResult placeOrder(Long memberId, List<OrderLineRequest> items) {
        Order order = orderDomainService.placeOrder(memberId, items);
        List<OrderLineInfo> orderLines = order.getOrderLines().stream()
            .map(ol -> new OrderLineInfo(ol.getProductId(), ol.getQuantity(), ol.getUnitPrice()))
            .collect(Collectors.toList());
        return new OrderResult(order.getId(), order.getStatus(), order.getTotalAmount(), orderLines);
    }

    public record OrderResult(Long orderId, String status, long totalAmount, List<OrderLineInfo> orderLines) {}

    public record OrderLineInfo(Long productId, int quantity, long unitPrice) {}
}
