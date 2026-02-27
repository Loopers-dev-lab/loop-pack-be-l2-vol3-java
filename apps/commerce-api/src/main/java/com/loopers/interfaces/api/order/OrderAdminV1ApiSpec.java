package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "주문 관리자 API")
public interface OrderAdminV1ApiSpec {

    @Operation(summary = "전체 주문 목록 조회", description = "관리자가 전체 주문 목록을 페이지 단위로 조회합니다.")
    ApiResponse<OrderAdminV1Dto.OrderListResponse> getOrders(
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 당 나타낼 데이터 개수", example = "20")
            @RequestParam(defaultValue = "20") int size
    );

    @Operation(summary = "단일 주문 상세 조회", description = "관리자가 특정 주문의 상세 내역을 조회합니다.")
    ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(
            @Parameter(description = "주문 ID") long orderId
    );
}
