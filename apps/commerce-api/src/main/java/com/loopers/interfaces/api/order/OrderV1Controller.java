package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApp;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemCommand;
import com.loopers.application.queue.QueueApp;
import com.loopers.config.QueueProperties;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderApp orderApp;
    private final OrderFacade orderFacade;
    private final QueueApp queueApp;
    private final QueueProperties queueProperties;

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> createOrder(
            @RequestHeader(value = "X-Entry-Token", required = false) String entryToken,
            @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        Long memberId = request.memberId();

        if (queueProperties.enabled()) {
            queueApp.validateToken(memberId, entryToken);
        }

        List<OrderItemCommand> items = request.items().stream()
                .map(OrderV1Dto.OrderItemRequest::toCommand)
                .toList();

        OrderInfo info = orderFacade.createOrder(memberId, items, request.userCouponId());

        if (queueProperties.enabled()) {
            queueApp.consumeToken(memberId);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(OrderV1Dto.OrderResponse.from(info)));
    }

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderListResponse>> getOrders(
            @RequestParam Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Pageable pageable
    ) {
        Page<OrderInfo> orders = orderApp.getMyOrders(
                memberId,
                startDate != null ? startDate.atStartOfDay() : null,
                endDate != null ? endDate.plusDays(1).atStartOfDay() : null,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.success(OrderV1Dto.OrderListResponse.from(orders)));
    }

    @GetMapping("/{orderId}")
    @Override
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> getOrder(
            @PathVariable String orderId,
            @RequestParam Long memberId
    ) {
        OrderInfo info = orderApp.getMyOrder(memberId, orderId);
        return ResponseEntity.ok(ApiResponse.success(OrderV1Dto.OrderResponse.from(info)));
    }

    @PatchMapping("/{orderId}/cancel")
    @Override
    public ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> cancelOrder(
            @PathVariable String orderId,
            @Valid @RequestBody OrderV1Dto.CancelOrderRequest request
    ) {
        OrderInfo info = orderFacade.cancelOrder(request.memberId(), orderId);
        return ResponseEntity.ok(ApiResponse.success(OrderV1Dto.OrderResponse.from(info)));
    }
}
