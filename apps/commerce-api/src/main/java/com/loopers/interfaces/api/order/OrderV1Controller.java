package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderService;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller implements OrderV1ApiSpec {

    private final OrderService orderService;

    @PostMapping
    @Override
    public ApiResponse<OrderV1Dto.OrderCreateResponse> createOrder(@Valid @RequestBody OrderV1Dto.OrderCreateRequest request) {
        List<OrderDomainService.OrderLineRequest> items = request.items().stream()
            .map(item -> new OrderDomainService.OrderLineRequest(item.productId(), item.quantity()))
            .collect(Collectors.toList());
        OrderService.OrderResult result = orderService.placeOrder(request.memberId(), items);
        return ApiResponse.success(OrderV1Dto.OrderCreateResponse.from(result));
    }
}
