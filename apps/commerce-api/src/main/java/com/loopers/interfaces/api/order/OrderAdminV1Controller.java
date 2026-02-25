package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderFacade orderFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<OrderAdminV1Dto.OrderSummaryResponse>> getAll(Pageable pageable) {
        Page<OrderAdminV1Dto.OrderSummaryResponse> response = orderFacade.getAll(pageable)
            .map(OrderAdminV1Dto.OrderSummaryResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderAdminV1Dto.OrderDetailResponse> getOrder(@PathVariable Long orderId) {
        OrderDetailInfo info = orderFacade.getOrder(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderDetailResponse.from(info));
    }
}
