package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order Admin API", description = "주문 관리 API")
public interface OrderAdminApiV1Spec {

    // Query

    @Operation(
            summary = "주문 목록 조회 (Admin)",
            description = "전체 주문을 최신순으로 페이징 조회합니다."
    )
    ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>> list(
            OrderRequest.ListAll request
    );

    @Operation(
            summary = "주문 상세 조회 (Admin)",
            description = "특정 주문의 상세 정보를 조회합니다."
    )
    ApiResponse<OrderAdminV1Dto.OrderResponse> detail(Long orderId);
}
