package com.loopers.interfaces.api.order.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.order.OrderService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/orders")
public class OrderV1AdminApi implements OrderV1AdminApiSpec {

    private final OrderService orderService;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<AdminOrderDto.OrderListResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var orders = orderService.getOrders(new PageSize(page, size));
        return ApiResponse.success(
                new PageResponse<>(
                        orders.content().stream()
                                .map(AdminOrderDto.OrderListResponse::from)
                                .toList(),
                        orders.hasNext()
                )
        );
    }
}