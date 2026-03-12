package com.loopers.interfaces.api.order;

import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Order V1 API", description = "고객 주문 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "새로운 주문을 생성합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> createOrder(MemberModel member, OrderV1Dto.CreateOrderRequest request);

    @Operation(summary = "내 주문 목록 조회", description = "내 주문 목록을 조회합니다.")
    ApiResponse<Page<OrderV1Dto.OrderSummaryResponse>> getMyOrders(MemberModel member, Pageable pageable);

    @Operation(summary = "주문 상세 조회", description = "주문 상세 정보를 조회합니다.")
    ApiResponse<OrderV1Dto.OrderDetailResponse> getById(MemberModel member, Long orderId);
}
