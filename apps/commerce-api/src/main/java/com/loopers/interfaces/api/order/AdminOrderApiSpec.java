package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Order API", description = "주문 관리 어드민 API")
public interface AdminOrderApiSpec {

    @Operation(summary = "주문 목록 조회", description = "전체 주문 목록을 페이지네이션으로 조회합니다.")
    ApiResponse<AdminOrderResponse.OrderListResponse> getOrders(String ldap, int page, int size);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 정보를 조회합니다.")
    ApiResponse<AdminOrderResponse.OrderDetail> getOrder(String ldap, Long orderId);
}
