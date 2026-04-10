package com.loopers.interfaces.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.dto.OrderResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.Login;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.order.dto.OrderRequest;
import com.loopers.interfaces.order.dto.OrderResponse;
import jakarta.validation.Valid;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderResponse.OrderSummary> create(
        @Login LoginUser loginUser,
        @Valid @RequestBody OrderRequest.Create request
    ) {
        return ApiResponse.success(
                OrderResponse.OrderSummary.from(
                        orderFacade.createOrder(loginUser.id(), request.toCriteria())));
    }

    @GetMapping
    @Override
    public ApiResponse<OrderResponse.ListResponse> list(
        @Login LoginUser loginUser,
        @RequestParam ZonedDateTime startAt,
        @RequestParam ZonedDateTime endAt
    ) {
        List<OrderResult.OrderSummary> results =
                orderFacade.getMyOrders(
                        loginUser.id(),
                        new OrderRequest.ListRequest(startAt, endAt).toCriteria());

        return ApiResponse.success(
                new OrderResponse.ListResponse(
                        results.stream().map(OrderResponse.OrderSummary::from).toList()));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderResponse.OrderDetail> getById(
        @Login LoginUser loginUser,
        @PathVariable Long orderId
    ) {
        return ApiResponse.success(
                OrderResponse.OrderDetail.from(
                        orderFacade.getMyOrderDetail(loginUser.id(), orderId)));
    }

    @PatchMapping("/{orderId}/items/{orderItemId}/cancel")
    @Override
    public ApiResponse<Object> cancelItem(
        @Login LoginUser loginUser,
        @PathVariable Long orderId,
        @PathVariable Long orderItemId
    ) {
        orderFacade.cancelMyOrderItem(loginUser.id(), orderId, orderItemId);
        return ApiResponse.success();
    }
}
