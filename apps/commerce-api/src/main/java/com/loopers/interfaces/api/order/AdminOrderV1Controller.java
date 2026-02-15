package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.PageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class AdminOrderV1Controller implements AdminOrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @GetMapping
    @Override
    public ApiResponse<AdminOrderV1Dto.OrderPageResponse> getAllOrders(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<OrderInfo> result = orderFacade.getAllOrders(page, size);
        return ApiResponse.success(AdminOrderV1Dto.OrderPageResponse.from(result));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<AdminOrderV1Dto.OrderDetailResponse> getOrderDetail(@PathVariable Long orderId) {
        OrderDetailInfo info = orderFacade.getOrderDetail(orderId);
        return ApiResponse.success(AdminOrderV1Dto.OrderDetailResponse.from(info));
    }
}
