package com.loopers.interfaces.api.order.v1;

import java.time.LocalDate;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 API", description = "대고객 주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
            summary = "주문 생성 API",
            description = "상품 ID와 수량 목록을 입력받아 주문을 생성합니다."
    )
    ApiResponse<OrderDto.CreateOrderResponse> createOrder(Long userId, OrderDto.CreateOrderRequest request);

    @Operation(
            summary = "주문 목록 조회 API",
            description = "인증된 사용자 본인의 주문 목록을 페이지 단위로 조회합니다."
    )
    ApiResponse<PageResponse<OrderDto.OrderListResponse>> getOrders(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    );

    @Operation(
            summary = "주문 상세 조회 API",
            description = "주문 상세 정보를 조회합니다."
    )
    ApiResponse<OrderDto.OrderDetailResponse> getOrder(Long userId, Long orderId);
}
