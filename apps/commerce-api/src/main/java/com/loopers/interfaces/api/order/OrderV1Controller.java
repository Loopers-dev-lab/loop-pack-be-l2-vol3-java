package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCreateCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.OrderItemInfo;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;
    private final OrderApplicationService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
            @LoginUser Long userId,
            @Valid @RequestBody OrderV1Dto.CreateRequest request
    ) {
        OrderInfo order = orderFacade.createOrder(OrderCreateCommand.from(userId, request));
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(order));
    }

    @GetMapping
    public ApiResponse<List<OrderV1Dto.OrderResponse>> getOrders(
            @LoginUser Long userId,
            @RequestParam String startAt,
            @RequestParam String endAt
    ) {
        ZonedDateTime parsedStartAt = parseZonedDateTime(startAt);
        ZonedDateTime parsedEndAt = parseZonedDateTime(endAt);

        List<OrderV1Dto.OrderResponse> orders = orderService.getOrders(userId, parsedStartAt, parsedEndAt)
                                                            .stream()
                                                            .map(OrderV1Dto.OrderResponse::from)
                                                            .toList();
        return ApiResponse.success(orders);
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderDetailResponse> getOrder(
            @LoginUser Long userId,
            @PathVariable Long orderId
    ) {
        OrderInfo order = orderService.getOrder(userId, orderId);
        List<OrderItemInfo> items = orderService.getOrderItems(orderId);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(order, items));
    }

    private ZonedDateTime parseZonedDateTime(String value) {
        String normalized = value.replace(" ", "+");
        return ZonedDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
