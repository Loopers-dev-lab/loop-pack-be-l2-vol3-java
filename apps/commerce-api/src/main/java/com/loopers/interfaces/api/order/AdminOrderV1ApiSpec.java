package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Order V1 API", description = "어드민 주문 API 입니다.")
public interface AdminOrderV1ApiSpec {

    @Operation(summary = "주문 목록 조회", description = "전체 주문 목록을 페이지 단위로 조회합니다.")
    ApiResponse<AdminOrderV1Dto.OrderPageResponse> getAllOrders(int page, int size);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 내역을 조회합니다.")
    ApiResponse<AdminOrderV1Dto.OrderDetailResponse> getOrderDetail(Long orderId);
}
