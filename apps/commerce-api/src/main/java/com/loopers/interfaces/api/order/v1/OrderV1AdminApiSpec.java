package com.loopers.interfaces.api.order.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order V1 Admin API", description = "주문 어드민 API 입니다.")
public interface OrderV1AdminApiSpec {

    @Operation(
            summary = "주문 목록 조회",
            description = "전체 주문 목록을 페이지 단위로 조회합니다."
    )
    ApiResponse<PageResponse<AdminOrderDto.OrderListResponse>> getOrders(int page, int size);
}