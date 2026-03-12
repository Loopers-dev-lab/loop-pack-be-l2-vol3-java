package com.loopers.interfaces.api.order.admin;

import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<Page<OrderAdminV1Dto.OrderSummaryResponse>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderModel> orders = orderService.getAllForAdmin(PageRequest.of(page, size));
        Page<OrderAdminV1Dto.OrderSummaryResponse> response = orders.map(
            OrderAdminV1Dto.OrderSummaryResponse::from
        );
        return ApiResponse.success(response);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(@PathVariable Long orderId) {
        OrderModel order = orderService.getOrderForAdmin(orderId);
        List<OrderItemModel> items = orderService.getOrderItems(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(order, items));
    }
}
