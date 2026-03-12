package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.OrderResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 주문 사용자 API.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderApplicationService orderApplicationService;

    /**
     * 주문 생성.
     */
    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponseDto> placeOrder(
            @UserId Long userId,
            @RequestBody OrderV1Dto.PlaceOrderRequestDto request) {
        OrderResult result = orderApplicationService.placeOrder(userId, request.toServiceRequest());
        return ApiResponse.success(OrderV1Dto.OrderResponseDto.from(result));
    }

    /**
     * 할인 적용 주문 생성.
     */
    @PostMapping("/with-discount")
    public ApiResponse<OrderV1Dto.OrderResponseDto> placeOrderWithDiscount(
            @UserId Long userId,
            @RequestBody OrderV1Dto.PlaceOrderWithDiscountRequestDto request) {
        OrderResult result = orderApplicationService.placeOrderWithDiscount(userId, request.toServiceRequest());
        return ApiResponse.success(OrderV1Dto.OrderResponseDto.from(result));
    }

    /**
     * 주문 상세 조회.
     */
    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponseDto> getOrder(
            @UserId Long userId,
            @PathVariable Long orderId) {
        OrderResult result = orderApplicationService.getOrder(orderId, userId);
        return ApiResponse.success(OrderV1Dto.OrderResponseDto.from(result));
    }

    /**
     * 내 주문 목록 조회.
     */
    @GetMapping
    public ApiResponse<OrderV1Dto.OrderListResponseDto> getOrders(
            @UserId Long userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        List<OrderResult> orders = orderApplicationService.getOrders(userId, offset, limit);
        long totalCount = orderApplicationService.countOrders(userId);

        List<OrderV1Dto.OrderResponseDto> dtos = orders.stream()
            .map(OrderV1Dto.OrderResponseDto::from)
            .toList();

        return ApiResponse.success(new OrderV1Dto.OrderListResponseDto(dtos, totalCount));
    }
}
