package com.loopers.interfaces.api.order;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.dto.CreateOrderApiReqDto;
import com.loopers.interfaces.api.order.dto.FindOrderApiResDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Order V1 API", description = "주문 API 입니다.")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "상품을 주문합니다.")
    ApiResponse<FindOrderApiResDto> createOrder(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password,
            CreateOrderApiReqDto request
    );

    @Operation(summary = "주문 목록 조회", description = "기간별 주문 목록을 조회합니다.")
    ApiResponse<List<FindOrderApiResDto>> findOrderList(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password,
            @Parameter(description = "조회 시작일시") LocalDateTime startAt,
            @Parameter(description = "조회 종료일시") LocalDateTime endAt
    );

    @Operation(summary = "주문 상세 조회", description = "주문 상세 정보를 조회합니다.")
    ApiResponse<FindOrderApiResDto> findOrder(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password,
            @Parameter(description = "주문 ID", required = true) Long orderId
    );
}
