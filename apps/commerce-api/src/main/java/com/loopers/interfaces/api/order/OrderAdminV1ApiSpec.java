package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Order Admin V1 API", description = "관리자 주문 API 입니다.")
public interface OrderAdminV1ApiSpec {

    @Operation(
        summary = "주문 목록 조회",
        description = "관리자가 주문 목록을 조회합니다."
    )
    ApiResponse<Page<OrderAdminV1Dto.OrderSummaryResponse>> getAll(Pageable pageable);

    @Operation(
        summary = "주문 상세 조회",
        description = "관리자가 단일 주문을 조회합니다."
    )
    ApiResponse<OrderAdminV1Dto.OrderDetailResponse> getOrder(
        @Parameter(description = "주문 ID", required = true) Long orderId
    );
}
