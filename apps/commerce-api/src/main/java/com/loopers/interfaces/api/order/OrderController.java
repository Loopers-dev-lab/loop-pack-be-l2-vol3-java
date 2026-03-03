package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.order.Order;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
import com.loopers.application.member.MemberAuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.loopers.application.order.query.OrderAccessRequest;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderFacade orderFacade;
    private final MemberAuthenticationService memberAuthenticationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderResponse> createOrder(
            @AuthMember Member member,
            @Valid @RequestBody OrderDto.CreateOrderRequest request
    ) {
        UUID userId = memberAuthenticationService.findDbIdByMember(member);
        Order order = orderFacade.create(request.toCommand(userId));
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto.OrderResponse> cancelOrder(
            @AuthMember Member member,
            @PathVariable UUID orderId
    ) {
        UUID userId = memberAuthenticationService.findDbIdByMember(member);
        Order order = orderFacade.cancel(new OrderAccessRequest(orderId, userId, false));
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(
            @AuthMember Member member,
            @PathVariable UUID orderId
    ) {
        UUID userId = memberAuthenticationService.findDbIdByMember(member);
        Order order = orderApplicationService.getById(new OrderAccessRequest(orderId, userId, false));
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @GetMapping
    public ApiResponse<OrderDto.OrderListResponse> listOrders(
            @AuthMember Member member,
            @Valid OrderDto.ListOrdersRequest request
    ) {
        UUID userId = memberAuthenticationService.findDbIdByMember(member);
        Page<Order> orders = orderApplicationService.listByUser(request.toQuery(userId));
        return ApiResponse.success(OrderDto.OrderListResponse.from(orders));
    }
}
