package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Order API", description = "주문 API")
public interface OrderApiV1Spec {

    // Command

    @Operation(
            summary = "주문 요청",
            description = "여러 상품을 한 번에 주문합니다. 재고 확인 및 차감 후 주문을 생성합니다."
    )
    ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            AuthenticatedUser user,
            OrderRequest.Place request
    );
}
