package com.loopers.interfaces.api.order;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.ZonedDateTime;

@Tag(name = "Order API", description = "주문 API")
public interface OrderApiSpec {

    @Operation(summary = "주문 생성", description = "상품을 주문합니다. items(직접 주문) 또는 cartItemIds(장바구니 기반 주문) 중 하나를 전달합니다. 장바구니 기반 주문 시 해당 장바구니 아이템이 삭제됩니다.")
    ApiResponse<OrderResponse.OrderCreateResponse> createOrder(
            @AuthUser User user, OrderRequest.CreateOrderRequest request);

    @Operation(summary = "주문 목록 조회", description = "본인의 주문 목록을 커서 기반으로 조회합니다. 날짜 범위 지정 가능 (미지정 시 최근 3개월).")
    ApiResponse<OrderResponse.OrderCursorListResponse> getOrders(
            @AuthUser User user, ZonedDateTime startAt, ZonedDateTime endAt, String cursor, int size);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 정보를 조회합니다. 스냅샷 기반입니다.")
    ApiResponse<OrderResponse.OrderDetail> getOrder(
            @AuthUser User user, Long orderId);

    @Operation(summary = "주문 취소", description = "PENDING 상태의 주문을 취소합니다. 재고 예약이 해제됩니다.")
    ApiResponse<Object> cancelOrder(@AuthUser User user, Long orderId);
}
