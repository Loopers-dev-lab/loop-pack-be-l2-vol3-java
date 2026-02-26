package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.domain.order.Order;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final UserRepository userRepository;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDto.OrderResponse> createOrder(
            @AuthUser User user,
            @Valid @RequestBody OrderDto.CreateOrderRequest request
    ) {
        Long userId = resolveUserId(user);
        Order order = orderApplicationService.create(request.toCommand(userId));
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @PatchMapping("/{orderId}/cancel")
    public ApiResponse<OrderDto.OrderResponse> cancelOrder(
            @AuthUser User user,
            @PathVariable Long orderId
    ) {
        Long userId = resolveUserId(user);
        Order order = orderApplicationService.cancel(orderId, userId, false);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderDto.OrderResponse> getOrder(
            @AuthUser User user,
            @PathVariable Long orderId
    ) {
        Long userId = resolveUserId(user);
        Order order = orderApplicationService.getById(orderId, userId, false);
        return ApiResponse.success(OrderDto.OrderResponse.from(order));
    }

    @GetMapping
    public ApiResponse<OrderDto.OrderListResponse> listOrders(
            @AuthUser User user,
            @RequestParam @NotNull @DateTimeFormat(pattern = "yyyyMMdd") LocalDate startAt,
            @RequestParam @NotNull @DateTimeFormat(pattern = "yyyyMMdd") LocalDate endAt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = resolveUserId(user);
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderApplicationService.listByUser(userId, startAt, endAt, pageable);
        return ApiResponse.success(OrderDto.OrderListResponse.from(orders));
    }

    private Long resolveUserId(User user) {
        return userRepository.findDbIdByUserId(user.id())
                .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));
    }
}
