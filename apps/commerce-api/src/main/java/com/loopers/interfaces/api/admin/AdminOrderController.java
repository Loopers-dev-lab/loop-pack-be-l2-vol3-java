package com.loopers.interfaces.api.admin;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.domain.order.Order;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderController {

    private final OrderApplicationService orderApplicationService;

    @GetMapping
    public ApiResponse<OrderDto.OrderListResponse> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderApplicationService.listAll(pageable);
        return ApiResponse.success(OrderDto.OrderListResponse.from(orders));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(@PathVariable Long orderId) {
        Order order = orderApplicationService.getById(orderId, null, true);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto.OrderResponse> cancelOrder(@PathVariable Long orderId) {
        Order order = orderApplicationService.cancel(orderId, null, true);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }
}
