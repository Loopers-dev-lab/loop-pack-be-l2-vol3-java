package com.loopers.interfaces.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Order V1 API", description = "주문 관리자 API 입니다.")
public interface AdminOrderV1ApiSpec {

    @Operation(
        summary = "주문 목록 조회",
        description = "전체 주문을 페이지네이션으로 조회합니다."
    )
    ApiResponse<OrderResponse.PageResponse> list(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") int page,
        @Parameter(description = "페이지 크기", example = "20") int size
    );

    @Operation(
        summary = "주문 상세 조회",
        description = "주문 상세를 조회합니다."
    )
    ApiResponse<OrderResponse.OrderDetail> getById(
        @Parameter(description = "주문 ID", required = true) Long orderId
    );

    @Operation(
        summary = "주문 아이템 취소",
        description = "주문 아이템을 취소합니다. 취소 시 재고가 복구됩니다."
    )
    ApiResponse<Object> cancelItem(
        @Parameter(description = "주문 ID", required = true) Long orderId,
        @Parameter(description = "주문 아이템 ID", required = true) Long orderItemId
    );
}
