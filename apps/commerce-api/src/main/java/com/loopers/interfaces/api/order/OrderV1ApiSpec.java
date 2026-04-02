package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Order V1 API", description = "주문 관련 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(
        summary = "주문 요청",
        description = "인증된 사용자가 상품을 주문합니다."
    )
    ApiResponse<OrderV1Dto.OrderDetailResponse> placeOrder(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        @Parameter(description = "대기열 입장 토큰", required = true) String queueToken,
        OrderV1Dto.PlaceOrderRequest request
    );

    @Operation(
        summary = "내 주문 목록 조회",
        description = "인증된 사용자의 기간별 주문 목록을 조회합니다."
    )
    ApiResponse<List<OrderV1Dto.OrderSummaryResponse>> getMyOrders(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        @Parameter(description = "조회 시작일(yyyy-MM-dd)", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @Parameter(description = "조회 종료일(yyyy-MM-dd)", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt
    );

    @Operation(
        summary = "내 주문 상세 조회",
        description = "인증된 사용자의 단일 주문 상세를 조회합니다."
    )
    ApiResponse<OrderV1Dto.OrderDetailResponse> getMyOrder(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        @Parameter(description = "주문 ID", required = true) Long orderId
    );
}
