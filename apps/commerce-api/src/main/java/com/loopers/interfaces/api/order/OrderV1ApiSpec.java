package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;

@Tag(name = "Order V1 API", description = "주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 요청", description = "상품을 직접 지정하여 주문합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> createOrder(@Parameter(hidden = true) AuthenticatedUser authUser, OrderV1Dto.CreateOrderRequest request);

    @Operation(summary = "장바구니 주문", description = "장바구니의 모든 항목으로 주문합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> createOrderFromCart(@Parameter(hidden = true) AuthenticatedUser authUser);

    @Operation(summary = "내 주문 목록 조회", description = "기간별 주문 목록을 조회합니다.")
    ApiResponse<OrderV1Dto.OrderPageResponse> getMyOrders(@Parameter(hidden = true) AuthenticatedUser authUser, LocalDate startAt, LocalDate endAt, int page, int size);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 내역을 조회합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> getMyOrderDetail(@Parameter(hidden = true) AuthenticatedUser authUser, Long orderId);

    @Operation(summary = "주문 취소", description = "주문을 취소합니다. 쿠폰이 적용된 경우 쿠폰이 복원됩니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> cancelOrder(@Parameter(hidden = true) AuthenticatedUser authUser, Long orderId);
}
