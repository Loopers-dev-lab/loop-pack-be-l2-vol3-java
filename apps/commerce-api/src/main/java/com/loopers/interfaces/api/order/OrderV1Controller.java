package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller implements OrderApiV1Spec {

    private final OrderFacade orderFacade;

    // Command

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            @AuthUser AuthenticatedUser user,
            @RequestBody @Valid OrderRequest.Place request) {
        OrderInfo info = orderFacade.placeOrder(user.id(), request.toCommand());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    // Query

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> getOrderDetail(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long orderId) {
        OrderInfo info = orderFacade.getOrderDetail(user.id(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>> listOrders(
            @AuthUser AuthenticatedUser user,
            @Valid OrderRequest.ListByUser request) {
        Page<OrderInfo.OrderSummary> orders = orderFacade.getOrderList(
                user.id(), request.startDateTime(), request.endDateTime(), request.toPageable());
        PageResponse<OrderV1Dto.OrderListResponse> pageResponse = PageResponse.from(orders, OrderV1Dto.OrderListResponse::from);
        return ApiResponse.success(pageResponse);
    }
}
