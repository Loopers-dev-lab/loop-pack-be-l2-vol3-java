package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.CreateOrderResponse> createOrder(
        @AuthUser AuthUserPrincipal user,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        OrderCommand command = new OrderCommand(
            user.getId(),
            request.toProductQuantities()
        );
        OrderInfo orderInfo = orderFacade.order(command);
        return ApiResponse.success(OrderV1Dto.CreateOrderResponse.from(orderInfo));
    }
}
