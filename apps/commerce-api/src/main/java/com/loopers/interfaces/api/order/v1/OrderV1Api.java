package com.loopers.interfaces.api.order.v1;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.order.OrderService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;

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
}
