package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Order Admin V1 API", description = "주문 관리 API")
public interface OrderAdminV1ApiSpec {

    @Operation(summary = "주문 목록 조회 (관리자)", description = "전체 주문 목록을 조회합니다.")
    ApiResponse<Page<OrderAdminV1Dto.OrderAdminSummaryResponse>> getAll(String adminLdap, Pageable pageable);

    @Operation(summary = "주문 상세 조회 (관리자)", description = "주문 상세 정보를 조회합니다.")
    ApiResponse<OrderAdminV1Dto.OrderAdminDetailResponse> getById(String adminLdap, Long orderId);
}
