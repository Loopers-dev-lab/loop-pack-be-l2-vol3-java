package com.loopers.interfaces.api.order.v1;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.order.OrderService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderV1Api implements OrderV1ApiSpec {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<OrderDto.CreateOrderResponse> createOrder(
            @LoginUser Long userId,
            @RequestBody @Valid OrderDto.CreateOrderRequest request
    ) {
        Long orderId = orderService.createOrder(request.toCart(userId));
        return ApiResponse.success(OrderDto.CreateOrderResponse.from(orderId));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<OrderDto.OrderListResponse>> getOrders(
            @LoginUser Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var orders = orderService.getOrders(userId, startDate, endDate, new PageSize(page,size));
        return ApiResponse.success(new PageResponse<>(
                orders.content()
                        .stream()
                        .map(OrderDto.OrderListResponse::from)
                        .toList(),
                orders.hasNext()
        ));
    }
}
