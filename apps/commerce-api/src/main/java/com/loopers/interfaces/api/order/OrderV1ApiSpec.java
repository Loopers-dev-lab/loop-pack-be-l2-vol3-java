package com.loopers.interfaces.api.order;

import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Tag(name = "주문 API")
public interface OrderV1ApiSpec {

    @Operation(summary = "주문 생성", description = "인증된 회원이 상품을 주문합니다.")
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            @LoginUser UserInfo loginUser,
            OrderV1Dto.OrderCreateRequest request);

    @Operation(summary = "주문 목록 조회", description = "인증된 회원이 기간별 자신의 주문 목록을 조회합니다.")
    ApiResponse<OrderV1Dto.OrderListResponse> getOrders(
            @LoginUser UserInfo loginUser,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt);

    @Operation(summary = "주문 상세 조회", description = "인증된 회원이 자신의 특정 주문 상세를 조회합니다.")
    ApiResponse<OrderV1Dto.OrderResponse> getOrder(
            @LoginUser UserInfo loginUser,
            long orderId);
}
