package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.process.checkout.OrderUseCase;
import com.loopers.domain.order.Order;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthMember;
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
    private final OrderUseCase orderUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderResponse> createOrder(
            @AuthMember Member member,
            @Valid @RequestBody OrderDto.CreateOrderRequest request
    ) {
        Order order = orderUseCase.create(request.toCommand(member.id().value()));
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto.OrderResponse> cancelOrder(
            @AuthMember Member member,
            @PathVariable UUID orderId
    ) {
        Order order = orderUseCase.cancel(new OrderAccessRequest(orderId, member.id().value(), false));
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(
            @AuthMember Member member,
            @PathVariable UUID orderId
    ) {
        Order order = orderApplicationService.getById(new OrderAccessRequest(orderId, member.id().value(), false));
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @GetMapping
    public ApiResponse<OrderDto.OrderListResponse> listOrders(
            @AuthMember Member member,
            @Valid OrderDto.ListOrdersRequest request
    ) {
        Page<Order> orders = orderApplicationService.listByUser(request.toQuery(member.id().value()));
        return ApiResponse.success(OrderDto.OrderListResponse.from(orders));
    }
}
