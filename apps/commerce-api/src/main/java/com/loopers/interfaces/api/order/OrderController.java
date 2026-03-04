package com.loopers.interfaces.api.order;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.OrderErrorType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController implements OrderApiSpec {

    private final OrderFacade orderFacade;

    public OrderController(OrderFacade orderFacade) {
        this.orderFacade = orderFacade;
    }

    @PostMapping
    @Override
    public ApiResponse<OrderResponse.OrderCreateResponse> createOrder(
            @AuthUser User user,
            @RequestBody OrderRequest.CreateOrderRequest request) {
        OrderFacade.OrderCreateResult result;

        boolean hasCartItems = request.cartItemIds() != null && !request.cartItemIds().isEmpty();
        boolean hasItems = request.items() != null && !request.items().isEmpty();

        String paymentMethod = request.paymentMethod() != null ? request.paymentMethod() : "CARD";

        if (hasCartItems) {
            result = orderFacade.createOrderFromCart(
                    user.getId(), user.getName().getValue(), request.ordererPhone(),
                    request.cartItemIds(), request.addressId(),
                    request.issuedCouponId(), request.pointAmount(), paymentMethod);
        } else if (hasItems) {
            List<OrderFacade.OrderItemCommand> commands = request.items().stream()
                    .map(item -> new OrderFacade.OrderItemCommand(item.productId(), item.quantity()))
                    .toList();
            result = orderFacade.createOrder(
                    user.getId(), user.getName().getValue(), request.ordererPhone(),
                    commands, request.addressId(),
                    request.issuedCouponId(), request.pointAmount(), paymentMethod);
        } else {
            throw new CoreException(
                    OrderErrorType.EMPTY_ORDER_ITEMS);
        }

        return ApiResponse.success(new OrderResponse.OrderCreateResponse(
                result.orderId(), result.orderNumber(), result.status(),
                result.totalAmount(), result.paymentId()));
    }

    @GetMapping
    @Override
    public ApiResponse<OrderResponse.OrderListResponse> getOrders(
            @AuthUser User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endAt) {
        ZonedDateTime start = startAt != null ? startAt : ZonedDateTime.now().minusMonths(3);
        ZonedDateTime end = endAt != null ? endAt : ZonedDateTime.now();

        OrderFacade.OrderListResult result = orderFacade.getOrders(user.getId(), start, end);

        List<OrderResponse.OrderSummary> summaries = result.orders().stream()
                .map(o -> new OrderResponse.OrderSummary(
                        o.orderId(), o.orderNumber(), o.status(),
                        o.totalAmount(), o.createdAt()))
                .toList();

        return ApiResponse.success(new OrderResponse.OrderListResponse(summaries));
    }

    @GetMapping("/{orderId}")
    @Override
    public ApiResponse<OrderResponse.OrderDetail> getOrder(
            @AuthUser User user,
            @PathVariable Long orderId) {
        OrderFacade.OrderDetailResult result = orderFacade.getOrderDetail(orderId, user.getId());

        List<OrderResponse.OrderItemDetail> items = result.items().stream()
                .map(item -> new OrderResponse.OrderItemDetail(
                        item.productName(), item.brandName(),
                        item.unitPrice(), item.quantity(), item.lineTotal()))
                .toList();

        return ApiResponse.success(new OrderResponse.OrderDetail(
                result.orderId(), result.orderNumber(), result.status(),
                result.ordererName(), result.ordererPhone(),
                result.receiverName(), result.receiverPhone(),
                result.zipCode(), result.addressLine1(), result.addressLine2(),
                result.subtotalAmount(), result.discountAmount(),
                result.pointUsedAmount(), result.shippingFee(), result.totalAmount(),
                items, result.createdAt()));
    }

    @DeleteMapping("/{orderId}")
    @Override
    public ApiResponse<Object> cancelOrder(
            @AuthUser User user,
            @PathVariable Long orderId) {
        orderFacade.cancelOrder(orderId, user.getId());
        return ApiResponse.success();
    }
}
