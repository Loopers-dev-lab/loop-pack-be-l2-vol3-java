package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.application.order.OrderInfo;
import com.loopers.application.order.PreOrderInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/orders")
public class OrderV1Controller {

    private final OrderFacade orderFacade;

    @PostMapping
    public ApiResponse<OrderV1Dto.OrderResponse> createOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @RequestHeader("X-Entry-Token") String entryToken,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        OrderInfo info = orderFacade.createOrder(loginId, rawPassword, entryToken, request.toOrderItemRequests(), request.couponId());
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @GetMapping
    public ApiResponse<Page<OrderV1Dto.OrderResponse>> getOrders(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<OrderInfo> infos = orderFacade.getOrders(loginId, rawPassword, pageable);
        return ApiResponse.success(infos.map(OrderV1Dto.OrderResponse::from));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> getOrderDetail(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.getOrderDetail(loginId, rawPassword, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @DeleteMapping("/{orderId}")
    public ApiResponse<OrderV1Dto.OrderResponse> cancelOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.cancelOrder(loginId, rawPassword, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    @PatchMapping("/{orderId}/approve")
    public ApiResponse<OrderV1Dto.OrderResponse> approveOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @PathVariable Long orderId
    ) {
        OrderInfo info = orderFacade.approveOrder(loginId, rawPassword, orderId);
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(info));
    }

    // ─────────────────────────────────────────────────────────────
    // 가주문 (Pre-Order) API
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/pre")
    public ApiResponse<OrderV1Dto.PreOrderResponse> createPreOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        PreOrderInfo info = orderFacade.createPreOrder(
            loginId, rawPassword, request.toOrderItemRequests(), request.couponId()
        );
        return ApiResponse.success(OrderV1Dto.PreOrderResponse.from(info));
    }

    @PutMapping("/pre/{preOrderId}")
    public ApiResponse<OrderV1Dto.PreOrderResponse> updatePreOrder(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String rawPassword,
        @PathVariable String preOrderId,
        @RequestBody OrderV1Dto.CreateOrderRequest request
    ) {
        PreOrderInfo info = orderFacade.updatePreOrder(
            loginId, rawPassword, preOrderId, request.toOrderItemRequests(), request.couponId()
        );
        return ApiResponse.success(OrderV1Dto.PreOrderResponse.from(info));
    }
}
