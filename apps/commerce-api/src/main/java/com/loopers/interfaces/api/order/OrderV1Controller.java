package com.loopers.interfaces.api.order;

import com.loopers.application.order.CreateOrderCommand;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.domain.PageResult;
import com.loopers.domain.order.Order;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderApplicationService orderApplicationService;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> createOrder(
        @AuthUser AuthenticatedUser authUser,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        CreateOrderCommand command = new CreateOrderCommand(
            authUser.userId(),
            request.items().stream()
                .map(i -> new CreateOrderCommand.LineItem(i.productId(), i.quantity()))
                .toList()
        );
        Order order = orderApplicationService.createOrder(command);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(order));
    }

    @PostMapping("/cart")
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> createOrderFromCart(@AuthUser AuthenticatedUser authUser) {
        Order order = orderApplicationService.createOrderFromCart(authUser.userId());
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(order));
    }

    @GetMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderPageResponse> getMyOrders(
        @AuthUser AuthenticatedUser authUser,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<Order> result = orderApplicationService.getMyOrders(authUser.userId(), startAt, endAt, page, size);
        return ApiResponse.success(OrderV1Dto.OrderPageResponse.from(result));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> getMyOrderDetail(
        @AuthUser AuthenticatedUser authUser,
        @PathVariable Long orderId
    ) {
        Order order = orderApplicationService.getMyOrder(authUser.userId(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(order));
    }
}
