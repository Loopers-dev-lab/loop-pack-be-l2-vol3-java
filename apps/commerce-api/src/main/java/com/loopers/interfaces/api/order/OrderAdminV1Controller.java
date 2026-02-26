package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/orders")
@RequiredArgsConstructor
public class OrderAdminV1Controller implements OrderAdminApiV1Spec {

    private final OrderFacade orderFacade;

    // Query

    @GetMapping
    @Override
    public ApiResponse<PageResponse<OrderAdminV1Dto.OrderListResponse>> list(
            OrderRequest.ListAll request) {
        Page<OrderInfo.OrderAdminSummary> orders = orderFacade.getAdminOrderList(request);
        PageResponse<OrderAdminV1Dto.OrderListResponse> pageResponse =
                PageResponse.from(orders, OrderAdminV1Dto.OrderListResponse::from);
        return ApiResponse.success(pageResponse);
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderAdminV1Dto.OrderResponse> detail(@PathVariable Long orderId) {
        OrderInfo info = orderFacade.getAdminOrderDetail(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(info));
    }
}
