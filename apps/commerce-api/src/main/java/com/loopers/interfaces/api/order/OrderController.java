package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.member.Member;
import com.loopers.domain.order.Order;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderDto.OrderResponse> createOrderFromCart(
            @LoginUser Member member,
            @RequestBody OrderDto.CreateFromCartRequest request
    ) {
        Order order = orderFacade.createOrderFromCart(member.getId(), request.cartItemIds(), request.couponId());
        return ApiResponse.success(OrderDto.OrderResponse.from(OrderInfo.from(order)));
    }

    @PostMapping("/direct")
    public ApiResponse<OrderDto.OrderResponse> createOrderDirect(
            @LoginUser Member member,
            @RequestBody OrderDto.CreateDirectRequest request
    ) {
        Order order = orderFacade.createOrder(request.toCommand(member.getId()));
        return ApiResponse.success(OrderDto.OrderResponse.from(OrderInfo.from(order)));
    }

    @GetMapping
    public ApiResponse<OrderDto.OrderListResponse> getOrders(@LoginUser Member member) {
        List<Order> orders = orderFacade.getOrdersByUserId(member.getId());
        List<OrderInfo> orderInfos = orders.stream().map(OrderInfo::from).toList();
        return ApiResponse.success(OrderDto.OrderListResponse.from(orderInfos));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(
            @LoginUser Member member,
            @PathVariable Long orderId
    ) {
        Order order = orderFacade.getOrder(member.getId(), orderId);
        return ApiResponse.success(OrderDto.OrderResponse.from(OrderInfo.from(order)));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto.OrderResponse> cancelOrder(
            @LoginUser Member member,
            @PathVariable Long orderId
    ) {
        Order order = orderFacade.cancelOrder(member.getId(), orderId);
        return ApiResponse.success(OrderDto.OrderResponse.from(OrderInfo.from(order)));
    }
}
