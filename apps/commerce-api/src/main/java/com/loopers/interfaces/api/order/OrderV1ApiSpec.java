package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.time.ZonedDateTime;
import java.util.List;

@Tag(name = "Order V1 API", description = "주문 API")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 생성",
        description = "상품·수량 목록으로 주문합니다. 로그인 필요. 주문 시점 상품 정보가 스냅샷으로 보존됩니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Schema(description = "주문 생성 요청 (항목 목록)")
        @Valid OrderV1Dto.CreateOrderRequest request
    );

    @Operation(
        summary = "주문 상세 조회",
        description = "특정 주문의 상세 내역을 조회합니다. 본인 주문만 조회 가능합니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> getOrder(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Parameter(description = "주문 ID", required = true)
        Long orderId
    );

    @Operation(
        summary = "주문 목록 조회",
        description = "기간 내 본인 주문 목록을 페이징 조회합니다. 시작일·종료일 필수."
    )
    ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Parameter(description = "조회 시작일시", required = true)
        ZonedDateTime start,
        @Parameter(description = "조회 종료일시", required = true)
        ZonedDateTime end,
        @Parameter(description = "페이지 (0부터)")
        int page,
        @Parameter(description = "페이지 크기")
        int size
    );

    @Operation(
        summary = "주문 취소",
        description = "주문을 취소합니다. 결제 완료 건은 재고 복구 후 취소됩니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> cancelOrder(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Parameter(description = "주문 ID", required = true)
        Long orderId
    );
}
