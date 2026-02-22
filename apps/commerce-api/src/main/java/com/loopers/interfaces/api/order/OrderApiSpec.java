package com.loopers.interfaces.api.order;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order API", description = "주문 API")
public interface OrderApiSpec {

    @Operation(summary = "주문 생성", description = "상품을 주문합니다. 재고 예약 후 PENDING 상태로 생성됩니다.")
    ApiResponse<OrderResponse.OrderCreateResponse> createOrder(
            @AuthUser User user, OrderRequest.CreateOrderRequest request);

    @Operation(summary = "주문 목록 조회", description = "본인의 주문 목록을 조회합니다. 날짜 범위 지정 가능 (미지정 시 최근 3개월).")
    ApiResponse<OrderResponse.OrderListResponse> getOrders(
            @AuthUser User user, String startAt, String endAt);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 정보를 조회합니다. 스냅샷 기반입니다.")
    ApiResponse<OrderResponse.OrderDetail> getOrder(
            @AuthUser User user, Long orderId);

    @Operation(summary = "주문 취소", description = "PENDING 상태의 주문을 취소합니다. 재고 예약이 해제됩니다.")
    ApiResponse<Object> cancelOrder(@AuthUser User user, Long orderId);
}
