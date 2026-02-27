package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestHeader("X-Loopers-UserId") Long userId,
        @RequestBody @Valid OrderV1Dto.CreateRequest request
    ) {
        List<OrderFacade.OrderItemRequest> itemRequests = request.items().stream()
            .map(OrderV1Dto.OrderItemRequest::toFacadeRequest)
            .toList();
        OrderInfo info = orderFacade.createOrder(userId, itemRequests);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }
}
