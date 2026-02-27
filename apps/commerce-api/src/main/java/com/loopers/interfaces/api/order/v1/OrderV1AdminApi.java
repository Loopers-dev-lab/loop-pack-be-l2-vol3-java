package com.loopers.interfaces.api.order.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.order.AdminOrderDetailResult;
import com.loopers.application.order.OrderResult;
import com.loopers.application.order.ReadOrderDetailUseCase;
import com.loopers.application.order.ReadOrdersUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/orders")
public class OrderV1AdminApi implements OrderV1AdminApiSpec {

    private final ReadOrdersUseCase readOrdersUseCase;
    private final ReadOrderDetailUseCase readOrderDetailUseCase;

    @GetMapping
    @Override
    public ApiResponse<PageResponse<AdminOrderDto.OrderListResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderResult> orders = readOrdersUseCase.execute(PageSize.withMaxSize(page, size));
        return ApiResponse.success(
                new PageResponse<>(
                        orders.content().stream()
                                .map(AdminOrderDto.OrderListResponse::from)
                                .toList(),
                        orders.hasNext()
                )
        );
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<AdminOrderDto.OrderDetailResponse> getOrder(
            @PathVariable Long orderId
    ) {
        AdminOrderDetailResult result = readOrderDetailUseCase.execute(orderId);
        return ApiResponse.success(AdminOrderDto.OrderDetailResponse.from(result));
    }
}
