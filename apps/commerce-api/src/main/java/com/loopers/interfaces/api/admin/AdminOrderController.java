package com.loopers.interfaces.api.admin;

import com.loopers.application.order.OrderAppService;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.order.Order;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/v1/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderAppService orderAppService;
    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<AdminOrderDto.OrderListResponse> getAll(@LoginAdmin String adminId) {
        List<Order> orders = orderAppService.getAll();
        List<OrderInfo> orderInfos = orders.stream().map(OrderInfo::from).toList();
        return ApiResponse.success(AdminOrderDto.OrderListResponse.from(orderInfos));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminOrderDto.OrderResponse> getById(
            @LoginAdmin String adminId,
            @PathVariable Long id
    ) {
        Order order = orderAppService.getById(id);
        return ApiResponse.success(AdminOrderDto.OrderResponse.from(OrderInfo.from(order)));
    }

    @PostMapping("/{id}/prepare")
    public ApiResponse<AdminOrderDto.OrderResponse> prepare(
            @LoginAdmin String adminId,
            @PathVariable Long id
    ) {
        Order order = orderFacade.prepareOrder(id);
        return ApiResponse.success(AdminOrderDto.OrderResponse.from(OrderInfo.from(order)));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<AdminOrderDto.OrderResponse> ship(
            @LoginAdmin String adminId,
            @PathVariable Long id
    ) {
        Order order = orderFacade.shipOrder(id);
        return ApiResponse.success(AdminOrderDto.OrderResponse.from(OrderInfo.from(order)));
    }

    @PostMapping("/{id}/deliver")
    public ApiResponse<AdminOrderDto.OrderResponse> deliver(
            @LoginAdmin String adminId,
            @PathVariable Long id
    ) {
        Order order = orderFacade.deliverOrder(id);
        return ApiResponse.success(AdminOrderDto.OrderResponse.from(OrderInfo.from(order)));
    }
}
