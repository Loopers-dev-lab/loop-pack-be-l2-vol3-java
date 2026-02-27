package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            @LoginUser UserInfo loginUser,
            @RequestBody OrderV1Dto.OrderCreateRequest request)
    {
        List<OrderCreateCommand.Item> items = request.items().stream()
                .map(item -> new OrderCreateCommand.Item(item.productId(), item.quantity()))
                .toList();
        OrderCreateCommand command = new OrderCreateCommand(items);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(
                orderFacade.create(loginUser.id(), command)));
    }

    @GetMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderListResponse> getOrders(
            @LoginUser UserInfo loginUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt)
    {
        return ApiResponse.success(OrderV1Dto.OrderListResponse.from(
                orderFacade.findAllByUserId(loginUser.id(), startAt, endAt)));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderResponse> getOrder(
            @LoginUser UserInfo loginUser,
            @PathVariable long orderId)
    {
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(
                orderFacade.findById(orderId, loginUser.id())));
    }
}
