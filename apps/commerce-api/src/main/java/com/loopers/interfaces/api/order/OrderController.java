package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.member.Member;
import com.loopers.domain.order.Order;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderFacade orderFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderResponse> createOrder(
        @AuthMember Member member,
        @Valid @RequestBody OrderDto.CreateRequest request
    ) {
        List<OrderFacade.OrderItemRequest> items = request.items().stream()
            .map(i -> new OrderFacade.OrderItemRequest(i.productId(), i.quantity()))
            .toList();
        Order order = orderFacade.createOrder(member.getId(), items);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @GetMapping
    public ApiResponse<List<OrderDto.OrderResponse>> getOrders(
        @AuthMember Member member,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt
    ) {
        ZonedDateTime start = startAt != null ? startAt.atZone(ZoneId.systemDefault()) : null;
        ZonedDateTime end = endAt != null ? endAt.atZone(ZoneId.systemDefault()) : null;
        List<OrderDto.OrderResponse> responses = orderFacade.getOrdersByMemberId(member.getId(), start, end)
            .stream()
            .map(OrderDto.OrderResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(
        @AuthMember Member member,
        @PathVariable Long orderId
    ) {
        Order order = orderFacade.getOrder(orderId, member.getId());
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Object> cancelOrder(
        @AuthMember Member member,
        @PathVariable Long orderId
    ) {
        orderFacade.cancelOrder(orderId, member.getId());
        return ApiResponse.success(null);
    }
}
