package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderV1Controller {

    private final OrderApplicationService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderV1Dto.OrderResponse>> getOrders(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<OrderV1Dto.OrderResponse> page = orderService.getAllOrders(pageable)
                                                         .map(OrderV1Dto.OrderResponse::from);
        return ApiResponse.success(PageResponse.from(page));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderDetailResponse> getOrder(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long orderId
    ) {
        OrderInfo order = orderService.getOrderById(orderId);
        List<OrderItemInfo> items = orderService.getOrderItems(orderId);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(order, items));
    }
}
