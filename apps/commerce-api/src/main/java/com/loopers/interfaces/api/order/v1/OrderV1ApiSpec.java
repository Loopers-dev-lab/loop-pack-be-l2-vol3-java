package com.loopers.interfaces.api.order.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "대고객 주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
            summary = "주문 생성 API",
            description = "상품 ID와 수량 목록을 입력받아 주문을 생성합니다."
    )
    ApiResponse<OrderDto.CreateOrderResponse> createOrder(Long userId, OrderDto.CreateOrderRequest request);
}