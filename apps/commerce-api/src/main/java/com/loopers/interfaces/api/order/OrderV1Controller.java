package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderV1Controller implements OrderApiV1Spec {

    private final OrderFacade orderFacade;

    // Command

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            @AuthUser AuthenticatedUser user,
            @RequestBody OrderRequest.Place request) {
        OrderInfo info = orderFacade.createOrder(user.id(), request);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
