package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.Response> createOrder(
            @RequestHeader(value = "X-User-Id") Long userId,
            @RequestBody OrderV1Dto.CreateRequest request
    ) {
        List<OrderFacade.OrderItemRequest> itemRequests = request.items().stream()
                .map(item -> new OrderFacade.OrderItemRequest(item.productId(), item.quantity()))
                .toList();

        OrderInfo orderInfo = orderFacade.createOrder(userId, itemRequests);
        return ApiResponse.success(OrderV1Dto.Response.from(orderInfo));
    }

    @GetMapping
    public ApiResponse<OrderV1Dto.PageResponse> getMyOrders(
            @RequestHeader(value = "X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderInfo> orders = orderFacade.getOrdersByUserId(userId, pageable);
        return ApiResponse.success(OrderV1Dto.PageResponse.from(orders));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.Response> getMyOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        OrderInfo orderInfo = orderFacade.getOrder(orderId, userId);
        return ApiResponse.success(OrderV1Dto.Response.from(orderInfo));
    }
}
