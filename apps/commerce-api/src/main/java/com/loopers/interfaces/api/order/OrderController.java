package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderDetail;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderSummary;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthenticatedUser;
import com.loopers.interfaces.auth.CurrentUser;
import com.loopers.interfaces.auth.LoginRequired;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class OrderController {

    private final OrderFacade orderFacade;

    @LoginRequired
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/orders")
    public ApiResponse<OrderDto.CreateOrderResponse> createOrder(@CurrentUser AuthenticatedUser user,
                                                                 @RequestBody OrderDto.CreateOrderRequest request) {

        OrderCommand orderCommand = new OrderCommand(
                request.items().stream()
                        .map(item -> new OrderCommand.Item(item.productId(), item.quantity()))
                        .toList(),
                request.couponId()
        );

        String orderId = orderFacade.createOrder(user.id(), orderCommand);
        OrderDto.CreateOrderResponse response = new OrderDto.CreateOrderResponse(orderId);
        return ApiResponse.success(response);
    }

    @LoginRequired
    @GetMapping("/api/v1/orders")
    public ApiResponse<List<OrderDto.SummaryResponse>> getList(@CurrentUser AuthenticatedUser user,
                                                            @RequestParam("startAt") LocalDate startAt,
                                                            @RequestParam("endAt") LocalDate endAt) {

        List<OrderSummary> orderSummaryList = orderFacade.getList(user.id(),
                ZonedDateTime.of(startAt, LocalTime.of(0, 0, 0), ZoneId.systemDefault()),
                ZonedDateTime.of(endAt, LocalTime.of(23, 59, 59), ZoneId.systemDefault()));
        List<OrderDto.SummaryResponse> summaryList = orderSummaryList.stream().map(OrderDto.SummaryResponse::from).toList();
        return ApiResponse.success(summaryList);
    }

    @LoginRequired
    @GetMapping("/api/v1/orders/{orderId}")
    public ApiResponse<OrderDto.DetailResponse> getDetail(@CurrentUser AuthenticatedUser user,
                                                          @PathVariable String orderId) {
        OrderDetail orderDetail = orderFacade.getDetail(user.id(), orderId);
        return ApiResponse.success(OrderDto.DetailResponse.from(orderDetail));
    }
}
