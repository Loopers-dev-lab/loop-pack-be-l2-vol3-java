package com.loopers.interfaces.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ApiResponse<OrderResponse.PageResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<OrderResult.OrderSummary> orderPage = orderFacade.getAllOrders(PageRequest.of(page, size));
        return ApiResponse.success(
                new OrderResponse.PageResponse(
                        orderPage.getNumber(),
                        orderPage.getSize(),
                        orderPage.getTotalElements(),
                        orderPage.getTotalPages(),
                        orderPage.getContent().stream()
                                .map(OrderResponse.OrderSummary::from)
                                .toList()));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderResponse.OrderDetail> getById(
        @PathVariable Long orderId
    ) {
        return ApiResponse.success(
                OrderResponse.OrderDetail.from(
                        orderFacade.getOrderDetail(orderId)));
    }

    @PatchMapping("/{orderId}/items/{orderItemId}/cancel")
    @Override
    public ApiResponse<Object> cancelItem(
        @PathVariable Long orderId,
        @PathVariable Long orderItemId
    ) {
        orderFacade.cancelOrderItem(orderId, orderItemId);
        return ApiResponse.success();
    }
}
