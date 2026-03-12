package com.loopers.interfaces.api.order.admin;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AdminInfo;
import com.loopers.interfaces.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<Page<OrderAdminV1Dto.OrderSummaryResponse>> getAll(
        @AdminUser AdminInfo admin,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderInfo> orders = orderFacade.getAllForAdmin(PageRequest.of(page, size));
        return ApiResponse.success(orders.map(OrderAdminV1Dto.OrderSummaryResponse::from));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(
        @AdminUser AdminInfo admin,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.getOrderForAdmin(orderId);
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(info));
    }
}
