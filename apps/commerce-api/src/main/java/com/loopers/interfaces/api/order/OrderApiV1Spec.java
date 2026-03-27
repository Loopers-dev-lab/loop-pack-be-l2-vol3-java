package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import com.loopers.interfaces.api.payment.PaymentV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order API", description = "주문 API")
public interface OrderApiV1Spec {

    // Command

    @Operation(
            summary = "주문 요청",
            description = "여러 상품을 한 번에 주문합니다. 재고 확인 및 차감 후 주문을 생성합니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            AuthenticatedUser user,
            OrderRequest.Place request
    );

    @Operation(
            summary = "주문 취소",
            description = "결제 완료된 주문을 취소합니다. PG 결제 취소 + 재고 복원 + 쿠폰 복원이 함께 처리됩니다."
    )
    ApiResponse<Void> cancelOrder(
            AuthenticatedUser user,
            Long orderId
    );

    // Query

    @Operation(
            summary = "주문 상세 조회",
            description = "본인의 주문 상세 정보를 조회합니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> getOrderDetail(
            AuthenticatedUser user,
            Long orderId
    );

    @Operation(
            summary = "주문별 결제 조회",
            description = "해당 주문에 연결된 결제 정보를 조회합니다."
    )
    ApiResponse<PaymentV1Dto.PaymentResponse> getPaymentByOrder(
            AuthenticatedUser user,
            Long orderId
    );

    @Operation(
            summary = "주문 목록 조회",
            description = "본인의 주문 내역을 기간별로 조회합니다."
    )
    ApiResponse<PageResponse<OrderV1Dto.OrderListResponse>> listOrders(
            AuthenticatedUser user,
            OrderRequest.ListByUser request
    );
}
