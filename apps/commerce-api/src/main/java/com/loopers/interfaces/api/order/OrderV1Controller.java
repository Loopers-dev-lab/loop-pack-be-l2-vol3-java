package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderDetailInfo;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.domain.PageResult;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import jakarta.validation.Valid;
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
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderFacade orderFacade;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> createOrder(
        @AuthUser User user,
        @Valid @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        List<OrderFacade.OrderItemRequest> items = request.items().stream()
            .map(i -> new OrderFacade.OrderItemRequest(i.productId(), i.quantity()))
            .collect(Collectors.toList());
        OrderDetailInfo info = orderFacade.createOrder(user.getId(), items);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info));
    }

    @PostMapping("/cart")
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> createOrderFromCart(@AuthUser User user) {
        OrderDetailInfo info = orderFacade.createOrderFromCart(user.getId());
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderPageResponse> getMyOrders(
        @AuthUser User user,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startAt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endAt,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<OrderInfo> result = orderFacade.getMyOrders(user.getId(), startAt, endAt, page, size);
        return ApiResponse.success(OrderV1Dto.OrderPageResponse.from(result));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderV1Dto.OrderDetailResponse> getMyOrderDetail(
        @AuthUser User user,
        @PathVariable Long orderId
    ) {
        OrderDetailInfo info = orderFacade.getMyOrderDetail(user.getId(), orderId);
        return ApiResponse.success(OrderV1Dto.OrderDetailResponse.from(info));
    }
}
