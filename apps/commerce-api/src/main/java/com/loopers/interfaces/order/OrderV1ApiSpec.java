package com.loopers.interfaces.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.order.dto.OrderRequest;
import com.loopers.interfaces.order.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.ZonedDateTime;

@Tag(name = "Order V1 API", description = "주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "새로운 주문을 생성합니다."
    )
    ApiResponse<OrderResponse.OrderSummary> create(
        @Parameter(hidden = true) LoginUser loginUser,
        @RequestBody(description = "주문 생성 요청 정보") OrderRequest.Create request
    );

    @Operation(
        summary = "내 주문 목록 조회",
        description = "기간 내 본인의 주문 목록을 조회합니다."
    )
    ApiResponse<OrderResponse.ListResponse> list(
        @Parameter(hidden = true) LoginUser loginUser,
        @Parameter(description = "시작일시") ZonedDateTime startAt,
        @Parameter(description = "종료일시") ZonedDateTime endAt
    );

    @Operation(
        summary = "내 주문 상세 조회",
        description = "본인의 주문 상세를 조회합니다."
    )
    ApiResponse<OrderResponse.OrderDetail> getById(
        @Parameter(hidden = true) LoginUser loginUser,
        @Parameter(description = "주문 ID", required = true) Long orderId
    );

    @Operation(
        summary = "주문 아이템 취소",
        description = "본인 주문의 아이템을 개별 취소합니다. 취소 시 재고가 복구됩니다."
    )
    ApiResponse<Object> cancelItem(
        @Parameter(hidden = true) LoginUser loginUser,
        @Parameter(description = "주문 ID", required = true) Long orderId,
        @Parameter(description = "주문 아이템 ID", required = true) Long orderItemId
    );
}
