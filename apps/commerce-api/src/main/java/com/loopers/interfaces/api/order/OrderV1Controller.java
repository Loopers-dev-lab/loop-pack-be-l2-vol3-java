package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class OrderV1Controller {

    private final OrderApplicationService orderApplicationService;

    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderV1Dto.OrderResponse> placeOrder(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @Valid @RequestBody OrderV1Dto.CreateRequest request
    ) {
        try {
            List<OrderDto.OrderLineCommand> commands = request.items().stream()
                .map(OrderV1Dto.OrderItemRequest::toCommand)
                .toList();

            OrderDto.OrderInfo order = orderApplicationService.placeOrder(userId, commands);
            return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new CoreException(ErrorType.CONFLICT, "동시 요청으로 실패했습니다. 다시 시도해주세요");
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @PathVariable Long orderId
    ) {
        OrderDto.OrderInfo order = orderApplicationService.getOrder(userId, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
    }

    @GetMapping("/api/v1/orders")
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @RequestHeader("X-Loopers-User-Id") Long userId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startAt,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endAt
    ) {
        try {
            List<OrderV1Dto.OrderResponse> orders = orderApplicationService.getOrders(userId, startAt, endAt).stream()
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
            OrderDto.OrderInfo order = orderApplicationService.cancelOrder(userId, orderId);
            return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new CoreException(ErrorType.CONFLICT, "동시 요청으로 실패했습니다. 다시 시도해주세요");
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/api-admin/v1/orders")
    public ApiResponse<OrderV1Dto.OrderPageResponse> getAdminOrders(Pageable pageable) {
        return ApiResponse.success(OrderV1Dto.OrderPageResponse.from(orderApplicationService.getAdminOrders(pageable)));
    }

    @GetMapping("/api-admin/v1/orders/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getAdminOrder(@PathVariable Long orderId) {
        OrderDto.OrderInfo order = orderApplicationService.getAdminOrder(orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
    }
}
