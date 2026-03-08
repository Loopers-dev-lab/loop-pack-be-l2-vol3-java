package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderV1Dto.OrderResponse> placeOrder(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @Valid @RequestBody OrderV1Dto.CreateRequest request
    ) {
        try {
            List<OrderDto.OrderLineCommand> commands = request.items().stream()
                .map(OrderV1Dto.OrderItemRequest::toCommand)
                .toList();

            OrderDto.OrderInfo order = orderFacade.placeOrder(userId, commands, request.couponId());
            return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @PathVariable Long orderId
    ) {
        OrderDto.OrderInfo order = orderFacade.getOrder(userId, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endAt
    ) {
        try {
            List<OrderV1Dto.OrderResponse> orders = orderFacade.getOrders(userId, startAt, endAt).stream()
                .map(OrderV1Dto.OrderResponse::from)
                .toList();
            return ApiResponse.success(orders);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> cancelOrder(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @PathVariable Long orderId
    ) {
        try {
            OrderDto.OrderInfo order = orderFacade.cancelOrder(userId, orderId);
            return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/api-admin/v1/orders")
    public ApiResponse<OrderV1Dto.OrderPageResponse> getAdminOrders(Pageable pageable) {
        return ApiResponse.success(OrderV1Dto.OrderPageResponse.from(orderFacade.getAdminOrders(pageable)));
    }

    @GetMapping("/api-admin/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getAdminOrder(@PathVariable Long orderId) {
        OrderDto.OrderInfo order = orderFacade.getAdminOrder(orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
    }
}
