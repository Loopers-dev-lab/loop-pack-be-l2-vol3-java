package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderAdminFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/orders")
public class OrderAdminV1Controller implements OrderAdminV1ApiSpec {

    private final OrderAdminFacade orderAdminFacade;

    @GetMapping
    public ApiResponse<OrderAdminV1Dto.OrderListResponse> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        return ApiResponse.success(OrderAdminV1Dto.OrderListResponse.from(
                orderAdminFacade.findAll(PageRequest.of(page, size))));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderAdminV1Dto.OrderResponse> getOrder(@PathVariable long orderId) {
        return ApiResponse.success(OrderAdminV1Dto.OrderResponse.from(
                orderAdminFacade.findById(orderId)));
    }
}
