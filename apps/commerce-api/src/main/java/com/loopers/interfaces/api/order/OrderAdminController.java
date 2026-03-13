package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.order.Order;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminController {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<List<OrderDto.OrderResponse>> getAllOrders() {
        List<OrderDto.OrderResponse> responses = orderFacade.getAllOrders().stream()
            .map(OrderDto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(@PathVariable Long orderId) {
        Order order = orderFacade.getOrder(orderId);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }
}
