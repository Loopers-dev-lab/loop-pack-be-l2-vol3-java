package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderQueryUseCase;
import com.loopers.interfaces.api.order.dto.OrderDetailResponse;
import com.loopers.interfaces.api.order.dto.OrderSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminController {

    private final OrderQueryUseCase orderQueryUseCase;

    public OrderAdminController(OrderQueryUseCase orderQueryUseCase) {
        this.orderQueryUseCase = orderQueryUseCase;
    }

    @GetMapping
    public ResponseEntity<List<OrderSummaryResponse>> getAllOrders() {
        List<OrderSummaryResponse> orders = orderQueryUseCase.getAllOrders().stream()
                .map(OrderSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        OrderQueryUseCase.OrderDetail detail = orderQueryUseCase.getOrderDetail(orderId);
        return ResponseEntity.ok(OrderDetailResponse.from(detail));
    }
}
