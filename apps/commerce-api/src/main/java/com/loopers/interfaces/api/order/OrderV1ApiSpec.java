package com.loopers.interfaces.api.order;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

@Tag(name = "Order V1 API", description = "주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 요청", description = "상품을 직접 지정하여 주문합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> createOrder(User user, OrderV1Dto.CreateOrderRequest request);

    @Operation(summary = "장바구니 주문", description = "장바구니의 모든 항목으로 주문합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> createOrderFromCart(User user);

    @Operation(summary = "내 주문 목록 조회", description = "기간별 주문 목록을 조회합니다.")
    ApiResponse<OrderV1Dto.OrderPageResponse> getMyOrders(User user, LocalDate startAt, LocalDate endAt, int page, int size);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 내역을 조회합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> getMyOrderDetail(User user, Long orderId);
}
