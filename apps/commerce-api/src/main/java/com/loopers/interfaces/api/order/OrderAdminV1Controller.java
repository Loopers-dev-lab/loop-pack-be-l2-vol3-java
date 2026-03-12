package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AdminUser;
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
    public ApiResponse<Page<OrderAdminV1Dto.OrderAdminSummaryResponse>> getAll(
        @AdminUser String adminLdap,
        Pageable pageable
    ) {
        Page<OrderAdminV1Dto.OrderAdminSummaryResponse> response = orderFacade.getAllOrders(pageable)
            .map(OrderAdminV1Dto.OrderAdminSummaryResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderAdminV1Dto.OrderAdminDetailResponse> getById(
        @AdminUser String adminLdap,
        @PathVariable(value = "orderId") Long orderId
    ) {
        OrderInfo.Detail info = orderFacade.getDetailForAdmin(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderAdminDetailResponse.from(info));
    }
}
