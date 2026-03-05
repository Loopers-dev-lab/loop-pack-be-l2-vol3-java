package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderHistoryInfo;
import com.loopers.application.order.OrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api-admin/v1/orders")
@RequiredArgsConstructor
public class OrderAdminV1Controller {

    private final OrderFacade orderFacade;

    @GetMapping
    public ApiResponse<OrderV1Dto.PageResponse> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderInfo> orders = orderFacade.getAllOrders(pageable);
        return ApiResponse.success(OrderV1Dto.PageResponse.from(orders));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.Response> getOrder(@PathVariable Long orderId) {
        OrderInfo orderInfo = orderFacade.getOrderForAdmin(orderId);
        return ApiResponse.success(OrderV1Dto.Response.from(orderInfo));
    }

    @GetMapping("/{orderId}/histories")
    public ApiResponse<List<OrderV1Dto.HistoryResponse>> getOrderHistories(@PathVariable Long orderId) {
        List<OrderHistoryInfo> histories = orderFacade.getOrderHistoriesForAdmin(orderId);
        List<OrderV1Dto.HistoryResponse> response = histories.stream()
                .map(OrderV1Dto.HistoryResponse::from)
                .toList();
        return ApiResponse.success(response);
    }
}
